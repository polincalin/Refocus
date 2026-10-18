#!/usr/bin/env python3
"""
puente_firebase_v2.py
Corre en la Raspberry Pi (en una segunda terminal, junto al script de sensores).

Se suscribe al broker EMQX y guarda las lecturas en Firestore.

Novedad: ahora sigue los botones de la página web.
  - Cuando el wearable avisa "Sesión en curso"  -> abre una sesión nueva en Firestore.
  - Cuando avisa "Sesión finalizada" (o se desconecta) -> guarda lo pendiente y cierra la sesión.

Estructura en Firestore:
    sesiones/{sesion_id}                 -> inicio, fin
    sesiones/{sesion_id}/lecturas/{auto} -> una lectura promediada por ventana de INTERVALO_S

Entiende tres formatos de mensaje de sensores:
  1. SenML (lista):  [{"bn": "...", "bt": 1728235343.1}, {"n": "bpm", "v": 80}, ...]
  2. {"ts": <ms>, "values": {...}}
  3. {"timestamp": <s>, "id_nodo": "1", "variables": {...}}

Instalación (dentro del entorno virtual):  pip install paho-mqtt firebase-admin
"""

import json
import queue
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt
import firebase_admin
from firebase_admin import credentials, firestore

# ---------------- Configuración ----------------
BROKER = "ab111316.ala.us-east-1.emqxsl.com"
PORT = 8883
MQTT_USER = "refocus"
MQTT_PASS = "refocus"
TOPIC = "sensores"                   # lecturas del sensor
TOPIC_ESTADO = "estado"      # avisos de la Raspberry (sesión en curso / finalizada)
CLIENT_ID = "puente_firebase"        # distinto al del script de sensores

# Cada cuántos segundos se guarda un documento en Firestore.
# 1 s -> 3.600 escrituras por hora (el plan gratis permite ~20.000 al día)
INTERVALO_S = 1.0

# ---------------- Firebase ----------------
cred = credentials.Certificate("firebase-credentials.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

cola = queue.Queue()
FIN = object()


# ---------------- Interpretar el mensaje ----------------
def a_datetime(t):
    """Acepta segundos o milisegundos y devuelve una fecha en UTC."""
    t = float(t)
    if t > 1e11:
        t /= 1000.0
    return datetime.fromtimestamp(t, tz=timezone.utc)


def interpretar(payload):
    """Devuelve (fecha, nodo, valores) o None si no entiende el mensaje."""
    datos = json.loads(payload.decode())
    ts = None
    nodo = None
    valores = {}

    if isinstance(datos, list):                       # formato SenML
        for r in datos:
            if "bt" in r:
                ts = a_datetime(r["bt"])
            if "bn" in r:
                nodo = r["bn"]
            nombre = r.get("n")
            if nombre is None:
                continue
            for clave in ("v", "vs", "vb"):
                if clave in r:
                    valores[nombre] = r[clave]
                    break

    elif isinstance(datos, dict):                     # formatos con "values" o "variables"
        valores = dict(datos.get("values") or datos.get("variables") or {})
        if "ts" in datos:
            ts = a_datetime(datos["ts"])
        elif "timestamp" in datos:
            ts = a_datetime(datos["timestamp"])
        nodo = datos.get("id_nodo") or valores.get("id_nodo")

    else:
        return None

    valores.pop("id_nodo", None)
    valores.pop("identity1", None)
    if ts is None:
        ts = datetime.now(timezone.utc)
    if nodo is not None:
        nodo = str(nodo)
    return ts, nodo, valores


def agregar_derivados(v):
    """Calcula movimiento y giro_total por cada lectura (antes de promediar)."""
    try:
        ax, ay, az = float(v["accel_x"]), float(v["accel_y"]), float(v["accel_z"])
        # Tu script de sensores ya resta la calibración y entrega en "g":
        # en reposo la magnitud es ~1 g, así que se resta 1 (no 9.81).
        v["movimiento"] = abs((ax * ax + ay * ay + az * az) ** 0.5 - 1.0)
    except (KeyError, TypeError, ValueError):
        pass
    try:
        gx, gy, gz = float(v["giro_x"]), float(v["giro_y"]), float(v["giro_z"])
        v["giro_total"] = (gx * gx + gy * gy + gz * gz) ** 0.5
    except (KeyError, TypeError, ValueError):
        pass


def es_numero(x):
    return isinstance(x, (int, float)) and not isinstance(x, bool)


# ---------------- Guardar en Firestore (hilo aparte) ----------------
def guardar(lecturas_ref, sesion_id, numericos, otros, nodo, ts, muestras):
    doc = {"sesion_id": sesion_id, "timestamp": ts, "muestras": muestras}
    if nodo is not None:
        doc["nodo"] = nodo
    for clave, lista in numericos.items():
        if clave == "bpm":
            lista = [x for x in lista if x > 0]   # bpm = 0 significa "sin pulso detectado"
            if not lista:
                continue
        doc[clave] = round(sum(lista) / len(lista), 4)
    doc.update(otros)
    try:
        lecturas_ref.add(doc)
        print("Guardado:", doc)
    except Exception as e:
        print("Error al guardar en Firestore:", e)


def trabajador():
    """Abre/cierra sesiones, junta las lecturas de cada ventana, las promedia y las guarda."""
    numericos, otros = {}, {}
    nodo, ts_ultimo, muestras = None, None, 0
    sesion_id, sesion_ref, lecturas_ref = None, None, None
    t0 = time.time()

    def abrir_sesion():
        nonlocal sesion_id, sesion_ref, lecturas_ref
        try:
            sesion_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            sesion_ref = db.collection("sesiones").document(sesion_id)
            sesion_ref.set({"inicio": firestore.SERVER_TIMESTAMP, "topic": TOPIC})
            lecturas_ref = sesion_ref.collection("lecturas")
            print(">>> Sesión abierta:", sesion_id)
        except Exception as e:
            print("Error al abrir la sesión en Firestore:", e)
            sesion_id = sesion_ref = lecturas_ref = None

    def vaciar():
        nonlocal muestras
        if muestras and lecturas_ref is not None:
            guardar(lecturas_ref, sesion_id, numericos, otros, nodo, ts_ultimo, muestras)
        numericos.clear()
        otros.clear()
        muestras = 0

    def cerrar_sesion():
        nonlocal sesion_id, sesion_ref, lecturas_ref
        vaciar()
        if sesion_ref is not None:
            try:
                sesion_ref.update({"fin": firestore.SERVER_TIMESTAMP})
                print(">>> Sesión cerrada:", sesion_id)
            except Exception as e:
                print("Error al cerrar la sesión en Firestore:", e)
        sesion_id = sesion_ref = lecturas_ref = None

    while True:
        try:
            item = cola.get(timeout=0.2)
        except queue.Empty:
            item = None

        if item is FIN:
            cerrar_sesion()
            return

        if item == "nueva":
            # Si ya hay una sesión abierta (p. ej. la Raspberry se reconectó), se ignora
            if sesion_ref is None:
                abrir_sesion()

        elif item == "fin":
            cerrar_sesion()

        elif item is not None:
            ts, n, valores = item
            if sesion_ref is None:
                abrir_sesion()    # por si el puente arrancó con la sesión ya empezada
            nodo = n or nodo
            ts_ultimo = ts
            muestras += 1
            agregar_derivados(valores)
            for clave, valor in valores.items():
                if es_numero(valor):
                    numericos.setdefault(clave, []).append(valor)
                else:
                    otros[clave] = valor

        if time.time() - t0 >= INTERVALO_S:
            vaciar()
            t0 = time.time()


# ---------------- MQTT ----------------
def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print(">>> CONEXIÓN EXITOSA: suscrito a", TOPIC, "y", TOPIC_ESTADO)
        client.subscribe([(TOPIC, 0), (TOPIC_ESTADO, 0)])
    elif rc == 4:
        print(">>> ERROR: Usuario o contraseña rechazados por EMQX.")
    elif rc == 5:
        print(">>> ERROR: Cliente no autorizado (revisa las credenciales).")
    else:
        print(f">>> ERROR DE CONEXIÓN. Código rc={rc}")


def on_message(client, userdata, msg):
    try:
        if msg.topic == TOPIC_ESTADO:
            estado = json.loads(msg.payload.decode()).get("estado", "")
            if estado == "Sesión en curso":
                cola.put("nueva")
            elif estado in ("Sesión finalizada", "Wearable desconectado"):
                cola.put("fin")
        else:
            resultado = interpretar(msg.payload)
            if resultado:
                cola.put(resultado)
    except Exception as e:
        print("Mensaje no entendido:", e)


try:    # paho-mqtt 2.x
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, client_id=CLIENT_ID)
except AttributeError:    # paho-mqtt 1.x
    client = mqtt.Client(client_id=CLIENT_ID)

client.username_pw_set(MQTT_USER, MQTT_PASS)
client.tls_set()
client.on_connect = on_connect
client.on_message = on_message

hilo = threading.Thread(target=trabajador)
hilo.start()

print("Conectando al broker EMQX Cloud...")
client.connect(BROKER, PORT, keepalive=60)
try:
    client.loop_forever()
except KeyboardInterrupt:
    print("\nCerrando y guardando lo que quede pendiente...")
finally:
    client.disconnect()
    cola.put(FIN)
    hilo.join(timeout=10)