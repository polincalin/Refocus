#!/usr/bin/env python3
"""
refocus.py - Wearable ReFocus (sensores + MQTT + Firestore + analítica en el borde)

Un solo script en la Raspberry Pi:
  - Lee IMU, GSR y pulso cada 1 s
  - Publica la lectura cruda en MQTT (topic sensores -> ThingsBoard)
  - Analítica en tiempo real: preprocesamiento, segmentación (30 s),
    alertas, tasa de enfoque y resumen por sesión
  - Guarda en Firestore un resumen por ventana y los eventos
  - Vibra cuando detecta el patrón de distracción
"""

import time
import json
import ssl
import math
from datetime import datetime, timezone

from mpu6050 import mpu6050
import board
import busio
import adafruit_ads1x15.ads1115 as ADS
from adafruit_ads1x15.analog_in import AnalogIn

from heartrate_monitor import HeartRateMonitor
import paho.mqtt.client as mqtt
import firebase_admin
from firebase_admin import credentials, firestore

dirIMU = 0x68
dirGSR = 0x48

MUESTRAS_CALIBRACION_IMU = 200
MUESTRAS_BASELINE_GSR = 30
SEGUNDOS_ESTABILIZACION_BPM = 5

# MQTT
BROKER = "ab111316.ala.us-east-1.emqxsl.com"
PORT = 8883
MQTT_USER = "refocus"
MQTT_PASS = "refocus"
TOPIC = "sensores"
TOPIC_COMANDOS = "comandos"
TOPIC_ESTADO = "estado"
TOPIC_ALERTAS = "alertas"
NODE_ID = "1"

# Analítica (ajusta los umbrales con tus sesiones reales)
BPM_MIN = 40              # BPM válido mínimo
BPM_MAX = 200             # BPM válido máximo
UMBRAL_BPM = 100          # BPM alto en reposo
UMBRAL_MOV = 0.2          # movimiento alto (g)
SEG_MOV_ALERTA = 10       # segundos seguidos con movimiento alto
UMBRAL_GSR_PCT = 0.20     # variación del GSR: +-20 % de la línea base
SEG_SIN_CONTACTO = 5      # segundos seguidos sin pulso = pérdida de contacto
TAM_VENTANA = 30          # muestras por ventana (1 muestra = 1 s)
PAUSA_VIBRACION_S = 30    # mínimo entre vibraciones

# Vibrador: pin GPIO (BCM). 17 = pin físico 11. Cámbialo si usas otro,
# o déjalo en None si aún no lo conectas.
PIN_VIBRADOR = 17


# Firebase
cred = credentials.Certificate("firebase-credentials.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

sesion_id = None
sesion_ref = None
inicio_sesion_ts = 0


# Sensores
imu = mpu6050(dirIMU)

i2c = busio.I2C(board.SCL, board.SDA)

ADC = ADS.ADS1115(i2c, address=dirGSR)

GSR = AnalogIn(ADC, 0)

hrm = HeartRateMonitor(
    print_raw=False,
    print_result=False
)

# Vibrador (opcional)
vibrador = None
if PIN_VIBRADOR is not None:
    try:
        from gpiozero import OutputDevice
        vibrador = OutputDevice(PIN_VIBRADOR)
    except Exception as e:
        print("No se pudo iniciar el vibrador:", e)


# Variables generales
comando = None
uid_pendiente = None    # uid que llegó junto con el último comando
uid_sesion = None       # dueño de la sesión en curso

estado_actual = "Listo. Falta calibrar"


def publicar_estado(texto):
    global estado_actual

    estado_actual = texto

    print(">>>", texto)

    mqtt_client.publish(
        TOPIC_ESTADO,
        json.dumps({"estado": texto}),
        retain=True
    )


def vibrar(segundos=0.5):
    if vibrador is not None:
        vibrador.on()
        time.sleep(segundos)
        vibrador.off()





# ============================================================
# FIRESTORE
# ============================================================

def abrir_sesion():
    global sesion_id, sesion_ref, inicio_sesion_ts

    try:
        inicio_sesion_ts = time.time()
        sesion_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        sesion_ref = db.collection("sesiones").document(sesion_id)
        sesion_ref.set({
            "inicio": firestore.SERVER_TIMESTAMP,
            "topic": TOPIC,
            "uid": uid_sesion
        })
        print(">>> Sesión abierta en Firestore:", sesion_id)

    except Exception as e:
        print("Error al abrir la sesión en Firestore:", e)
        sesion_id = sesion_ref = None


def cerrar_sesion():
    global sesion_id, sesion_ref

    if sesion_ref is not None:
        try:
            datos = resumen_sesion()
            datos["fin"] = firestore.SERVER_TIMESTAMP
            sesion_ref.update(datos)
            print(">>> Sesión cerrada en Firestore:", sesion_id)

        except Exception as e:
            print("Error al cerrar la sesión en Firestore:", e)

    sesion_id = sesion_ref = None


def guardar(subcoleccion, doc):
    """Guarda un documento en sesiones/{id}/{subcoleccion}."""
    if sesion_ref is None:
        return

    try:
        sesion_ref.collection(subcoleccion).add(doc)

    except Exception as e:
        print(f"Error al guardar en Firestore ({subcoleccion}):", e)


# ============================================================
# ANALÍTICA EN EL BORDE
# ============================================================

buffer_ventana = []     # muestras de la ventana actual
ultima_vibracion = 0

# Estado de las condiciones de alerta
racha = {"mov": 0, "contacto": 0}
cond_prev = {"movimiento": False, "gsr": False, "bpm": False}

# Duración (s) de las etapas previas a la sesión
etapas_s = {"calibracion": 0, "reposo": 0}
t_finalizar = 0           # instante en que se pidió finalizar


def nuevas_estadisticas():
    return {
        "muestras": 0,
        "muestras_mov_alto": 0,
        "n_alertas": 0,
        "n_distracciones": 0,
        "ventanas": 0,
        "ventanas_con_alerta": 0,
        "bpm": [],
        "mov": [],
        "giro": [],
        "gsr": [],
    }


estadisticas = nuevas_estadisticas()   # acumuladores de la sesión actual


def reiniciar_analitica():
    global ultima_vibracion, estadisticas
    buffer_ventana.clear()
    racha["mov"] = 0
    racha["contacto"] = 0
    for nombre in cond_prev:
        cond_prev[nombre] = False
    ultima_vibracion = 0
    estadisticas = nuevas_estadisticas()


# ---------- Estadísticos: media, desviación y máximo ----------

def estadisticos(valores):
    """Devuelve (media, desviación estándar, máximo) o None si no hay datos."""
    n = len(valores)
    if n == 0:
        return None

    media = sum(valores) / n
    desv = math.sqrt(sum((v - media) ** 2 for v in valores) / n)
    return media, desv, max(valores)


def agregar_estadisticos(destino, prefijo, valores):
    est = estadisticos(valores)
    if est:
        destino[prefijo + "_prom"] = round(est[0], 4)
        destino[prefijo + "_std"] = round(est[1], 4)
        destino[prefijo + "_max"] = round(est[2], 4)


# ---------- Resumen de toda la sesión ----------

def resumen_sesion():
    """Resumen de la sesión (se guarda al cerrarla)."""
    e = estadisticas
    duracion = int(time.time() - inicio_sesion_ts)

    resumen = {
        "duracion_s": duracion,
        "muestras": e["muestras"],
        "n_alertas": e["n_alertas"],
        "n_distracciones": e["n_distracciones"],
        "n_ventanas": e["ventanas"],
        "gsr_linea_base": round(baseline_gsr, 4),
        "etapas": {
            "calibracion_s": etapas_s["calibracion"],
            "reposo_s": etapas_s["reposo"],
            "sesion_s": duracion,
            "finalizacion_s": round(time.time() - t_finalizar, 2) if t_finalizar else 0,
        },
    }

    agregar_estadisticos(resumen, "bpm", e["bpm"])
    agregar_estadisticos(resumen, "mov", e["mov"])
    agregar_estadisticos(resumen, "giro", e["giro"])
    agregar_estadisticos(resumen, "gsr", e["gsr"])

    # % de lecturas con movimiento alto
    if e["muestras"]:
        resumen["pct_mov_alto"] = round(100 * e["muestras_mov_alto"] / e["muestras"], 1)

    # Tasa de enfoque: % de ventanas sin alertas
    if e["ventanas"]:
        sin_alerta = e["ventanas"] - e["ventanas_con_alerta"]
        resumen["tasa_enfoque"] = round(100 * sin_alerta / e["ventanas"], 1)

    return resumen


# ---------- 1. PREPROCESAMIENTO ----------

# Agrupa IMU + GSR + pulso en un solo registro
def agrupar(aceleracion, giro, voltaje, delta_gsr, bpm):
    return {
        "ts": datetime.now(timezone.utc),
        "movimiento": abs(math.sqrt(
            aceleracion["x"] ** 2 +
            aceleracion["y"] ** 2 +
            aceleracion["z"] ** 2
        ) - 1.0),
        "giro_total": math.sqrt(
            giro["x"] ** 2 +
            giro["y"] ** 2 +
            giro["z"] ** 2
        ),
        "gsr_voltage": voltaje,
        "gsr_delta": delta_gsr,
        "bpm": bpm,
    }


def preprocesar(r):
    # BPM fuera de rango = inválido (no entra a las estadísticas de BPM)
    r["bpm_valido"] = BPM_MIN <= r["bpm"] <= BPM_MAX

    # bpm = 0 significa "sin pulso detectado" (posible pérdida de contacto)
    r["sin_contacto"] = r["bpm"] <= 0

    r["nivel_mov"] = "alto" if r["movimiento"] > UMBRAL_MOV else "bajo"
    return r


# ---------- 3. ALERTAS ----------

def evaluar_alertas(r):
    """Devuelve (condiciones activas, alertas nuevas en esta lectura)."""

    # Movimiento > UMBRAL_MOV durante SEG_MOV_ALERTA segundos seguidos
    racha["mov"] = racha["mov"] + 1 if r["movimiento"] > UMBRAL_MOV else 0

    # Pérdida de contacto: sin pulso durante SEG_SIN_CONTACTO segundos seguidos
    racha["contacto"] = racha["contacto"] + 1 if r["sin_contacto"] else 0

    en_reposo = r["movimiento"] <= UMBRAL_MOV

    condiciones = {
        # Movimiento sostenido
        "movimiento": racha["mov"] >= SEG_MOV_ALERTA,

        # GSR fuera de +-20 % de la línea base
        "gsr": (
            baseline_gsr > 0
            and abs(r["gsr_delta"]) > UMBRAL_GSR_PCT * baseline_gsr
        ),

        # BPM alto en reposo o pérdida de contacto
        "bpm": (
            (r["bpm_valido"] and r["bpm"] > UMBRAL_BPM and en_reposo)
            or racha["contacto"] >= SEG_SIN_CONTACTO
        ),
    }

    activas = [nombre for nombre, activa in condiciones.items() if activa]

    # Alerta nueva = condición que se activa y antes no lo estaba
    nuevas = sum(1 for nombre in activas if not cond_prev[nombre])

    for nombre, activa in condiciones.items():
        cond_prev[nombre] = activa

    return activas, nuevas


# ---------- 2. SEGMENTACIÓN: ventanas de TAM_VENTANA segundos ----------

def resumir_ventana(lote):
    resumen = {
        "inicio": lote[0]["ts"],
        "muestras": len(lote),
        "etapa": "sesion",
        "n_alertas": sum(r["alertas_nuevas"] for r in lote),
        "con_alerta": any(r["alertas"] for r in lote),
    }

    agregar_estadisticos(resumen, "bpm", [r["bpm"] for r in lote if r["bpm_valido"]])
    agregar_estadisticos(resumen, "mov", [r["movimiento"] for r in lote])
    agregar_estadisticos(resumen, "giro", [r["giro_total"] for r in lote])
    agregar_estadisticos(resumen, "gsr", [r["gsr_delta"] for r in lote])

    return resumen


def cerrar_ventana():
    if not buffer_ventana:
        return

    resumen = resumir_ventana(buffer_ventana)
    buffer_ventana.clear()

    estadisticas["ventanas"] += 1
    estadisticas["ventanas_con_alerta"] += int(resumen["con_alerta"])

    # Firestore: un documento por ventana
    guardar("ventanas", resumen)


# ---------- Análisis de cada lectura ----------

def analizar(r):
    """Preprocesa, evalúa alertas, segmenta y detecta distracción."""
    global ultima_vibracion

    r = preprocesar(r)

    activas, nuevas = evaluar_alertas(r)
    r["alertas"] = activas
    r["alertas_nuevas"] = nuevas

    # Estadísticas de la sesión
    e = estadisticas
    e["muestras"] += 1
    if r["nivel_mov"] == "alto":
        e["muestras_mov_alto"] += 1
    e["n_alertas"] += nuevas
    e["mov"].append(r["movimiento"])
    e["giro"].append(r["giro_total"])
    e["gsr"].append(r["gsr_delta"])
    if r["bpm_valido"]:
        e["bpm"].append(r["bpm"])

    # Segmentación
    buffer_ventana.append(r)
    if len(buffer_ventana) >= TAM_VENTANA:
        cerrar_ventana()

    # Distracción: se cumplen 2 de las 3 condiciones -> vibra
    # (no más de una vez cada PAUSA_VIBRACION_S)
    if len(activas) >= 2 and time.time() - ultima_vibracion >= PAUSA_VIBRACION_S:
        print(">>> Distracción detectada:", ", ".join(activas))

        estadisticas["n_distracciones"] += 1
        ultima_vibracion = time.time()

        guardar("eventos", {
            "ts": r["ts"],
            "tipo": "posible_distraccion",
            "alertas": activas,
            "bpm": round(r["bpm"], 2),
            "movimiento": round(r["movimiento"], 4),
            "gsr_delta": round(r["gsr_delta"], 4),
        })

        # Aviso a la web (solo lo muestra el dueño de la sesión)
        mqtt_client.publish(
            TOPIC_ALERTAS,
            json.dumps({
                "alerta": "vibracion",
                "uid": uid_sesion,
                "ts": time.time()
            })
        )

        vibrar()

# ============================================================
# MQTT callbacks
# ============================================================

def on_connect(client, userdata, flags, rc, properties=None):

    if rc == 0:
        print(">>> CONEXIÓN EXITOSA: El broker EMQX aceptó la conexión.")
        client.subscribe(TOPIC_COMANDOS)
        publicar_estado(estado_actual)

    elif rc == 1:
        print(">>> ERROR: Versión de protocolo incorrecta.")

    elif rc == 4:
        print(">>> ERROR: Usuario o contraseña rechazados por EMQX.")

    elif rc == 5:
        print(">>> ERROR: Cliente no autorizado.")

    else:
        print(f">>> ERROR DE CONEXIÓN. Código rc={rc}")


def on_message(client, userdata, msg):

    global comando, uid_pendiente

    try:
        datos = json.loads(msg.payload.decode())
        uid_pendiente = datos.get("uid")
        comando = datos["comando"]

    except Exception:
        print(">>> Comando inválido:", msg.payload)


# Cliente MQTT
try:    # paho-mqtt 2.x
    mqtt_client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION1,
        client_id=NODE_ID
    )
except AttributeError:    # paho-mqtt 1.x
    mqtt_client = mqtt.Client(client_id=NODE_ID)

mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)

mqtt_client.tls_set(tls_version=ssl.PROTOCOL_TLS)

# Si la Raspberry se apaga inesperadamente,
# EMQX publica este estado.
mqtt_client.will_set(
    TOPIC_ESTADO,
    json.dumps({"estado": "Wearable desconectado"}),
    retain=True
)

mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message


print("Conectando al broker EMQX Cloud...")

mqtt_client.connect(BROKER, PORT, keepalive=60)

mqtt_client.loop_start()


# ============================================================
# CALIBRACIÓN DEL IMU
# ============================================================

def calibrar_imu():

    print(
        "\nCalibrando IMU: "
        "el sensor debe estar QUIETO y PLANO sobre una mesa."
    )

    suma_accel = {"x": 0, "y": 0, "z": 0}
    suma_giro = {"x": 0, "y": 0, "z": 0}

    for _ in range(MUESTRAS_CALIBRACION_IMU):

        a = imu.get_accel_data(g=True)
        g = imu.get_gyro_data()

        for eje in ("x", "y", "z"):
            suma_accel[eje] += a[eje]
            suma_giro[eje] += g[eje]

        time.sleep(0.01)

    offset_accel = {
        eje: suma_accel[eje] / MUESTRAS_CALIBRACION_IMU
        for eje in "xyz"
    }

    offset_giro = {
        eje: suma_giro[eje] / MUESTRAS_CALIBRACION_IMU
        for eje in "xyz"
    }

    # En reposo Z debe marcar aproximadamente 1 g
    offset_accel["z"] -= 1.0

    print("Calibración del IMU completada.")

    return {
        "accel": offset_accel,
        "giro": offset_giro
    }


# ============================================================
# LÍNEA BASE DEL GSR
# ============================================================

def calcular_baseline_gsr():

    print(
        f"\nCalculando línea base de GSR: "
        f"mantente en reposo {MUESTRAS_BASELINE_GSR} segundos..."
    )

    suma = 0

    for i in range(MUESTRAS_BASELINE_GSR):

        suma += GSR.voltage

        time.sleep(1)

        print(f"{i + 1}/{MUESTRAS_BASELINE_GSR}s", end="\r")

    baseline = suma / MUESTRAS_BASELINE_GSR

    print(f"\nLínea base de GSR: {baseline:.3f} V")

    return baseline


# ============================================================
# PROGRAMA PRINCIPAL
# ============================================================

calibracion_imu = None

baseline_gsr = 0

midiendo = False

pulso_activo = False


print("\nEsperando comandos...\n")


try:

    while True:

        cmd = comando
        uid_cmd = uid_pendiente
        comando = None


        # CALIBRAR

        if cmd == "calibrar":

            if midiendo:

                # IMPORTANTE:
                # no cambiamos el estado MQTT.
                # La sesión sigue en curso.
                print(">>> Finaliza la sesión antes de calibrar")

            else:

                publicar_estado("Calibrando: mantén el sensor quieto y plano")

                t_etapa = time.time()

                calibracion_imu = calibrar_imu()

                etapas_s["calibracion"] = round(time.time() - t_etapa, 1)

                publicar_estado("Calibración lista")


        # INICIAR

        elif cmd == "iniciar":

            if calibracion_imu is None:

                publicar_estado("Primero calibra el sensor")

            elif not midiendo:

                publicar_estado(
                    f"Preparando sesión: "
                    f"quédate en reposo {MUESTRAS_BASELINE_GSR} s"
                )

                t_etapa = time.time()

                baseline_gsr = calcular_baseline_gsr()

                hrm.start_sensor()

                pulso_activo = True

                print(
                    f"\nEstabilizando sensor de pulso "
                    f"({SEGUNDOS_ESTABILIZACION_BPM}s)..."
                )

                time.sleep(SEGUNDOS_ESTABILIZACION_BPM)

                etapas_s["reposo"] = round(time.time() - t_etapa, 1)

                midiendo = True

                reiniciar_analitica()

                uid_sesion = uid_cmd

                abrir_sesion()

                publicar_estado("Sesión en curso")


        # FINALIZAR

        elif cmd == "finalizar":

            if midiendo:

                midiendo = False

                hrm.stop_sensor()

                pulso_activo = False

                t_finalizar = time.time()

                # Guarda la ventana que quedó a medias
                cerrar_ventana()

                cerrar_sesion()

                # El IMU se calibra de nuevo en cada sesión
                calibracion_imu = None

                publicar_estado("Sesión finalizada")


        # LECTURA DE SENSORES

        if midiendo:

            try:

                aceleracion_cruda = imu.get_accel_data(g=True)

                giro_crudo = imu.get_gyro_data()

                voltaje = GSR.voltage

                # Aplica calibración del IMU
                aceleracion = {
                    eje: aceleracion_cruda[eje] - calibracion_imu["accel"][eje]
                    for eje in "xyz"
                }

                giro = {
                    eje: giro_crudo[eje] - calibracion_imu["giro"][eje]
                    for eje in "xyz"
                }

                delta_gsr = voltaje - baseline_gsr

                bpm = hrm.bpm

                print(
                    f"Accel: "
                    f"X={aceleracion['x']:.2f} "
                    f"Y={aceleracion['y']:.2f} "
                    f"Z={aceleracion['z']:.2f} g | "

                    f"Giro: "
                    f"X={giro['x']:.2f} "
                    f"Y={giro['y']:.2f} "
                    f"Z={giro['z']:.2f} deg/s | "

                    f"GSR: {voltaje:.3f} V (Δ{delta_gsr:+.3f}) | "

                    f"Pulso: {bpm:.1f} BPM"
                )


                # Mensaje SenML crudo (para ThingsBoard)

                mensaje_senml = [

                    {
                        "bn": f"urn:dev:refocus:{NODE_ID}:",
                        "bt": time.time(),
                        "n": "identity1",
                        "vs": "identity1"
                    },

                    {"n": "accel_x", "u": "g", "v": aceleracion["x"]},
                    {"n": "accel_y", "u": "g", "v": aceleracion["y"]},
                    {"n": "accel_z", "u": "g", "v": aceleracion["z"]},

                    {"n": "giro_x", "u": "deg/s", "v": giro["x"]},
                    {"n": "giro_y", "u": "deg/s", "v": giro["y"]},
                    {"n": "giro_z", "u": "deg/s", "v": giro["z"]},

                    {"n": "gsr_voltage", "u": "V", "v": voltaje},
                    {"n": "gsr_delta", "u": "V", "v": delta_gsr},

                    {"n": "bpm", "u": "bpm", "v": bpm}
                ]

                mqtt_client.publish(TOPIC, json.dumps(mensaje_senml))

                # ANALÍTICA: agrupa los 3 sensores y aplica las 5 funciones
                analizar(agrupar(aceleracion, giro, voltaje, delta_gsr, bpm))

                time.sleep(1)


            except OSError as e:

                if e.errno == 121:

                    time.sleep(0.05)

                    continue

                else:

                    raise


        else:

            time.sleep(0.1)


except KeyboardInterrupt:

    print("\nPrograma detenido.")


finally:

    if pulso_activo:

        hrm.stop_sensor()

    # Cierra lo que haya quedado abierto en Firestore
    cerrar_ventana()

    cerrar_sesion()

    if vibrador is not None:
        vibrador.off()

    try:

        info = mqtt_client.publish(
            TOPIC_ESTADO,
            json.dumps({"estado": "Wearable desconectado"}),
            retain=True
        )

        try:

            info.wait_for_publish(timeout=3)

        except TypeError:

            time.sleep(0.5)

    except Exception as e:

        print("No se pudo avisar la desconexión:", e)

    mqtt_client.loop_stop()

    mqtt_client.disconnect()

    print("Conexión MQTT cerrada.")