#!/usr/bin/env python3

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
NODE_ID = "1"


# Firebase
cred = credentials.Certificate("firebase-credentials.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

sesion_id = None
sesion_ref = None
lecturas_ref = None


# Sensores
imu = mpu6050(dirIMU)

i2c = busio.I2C(board.SCL, board.SDA)

ADC = ADS.ADS1115(i2c, address=dirGSR)

GSR = AnalogIn(ADC, 0)

hrm = HeartRateMonitor(
    print_raw=False,
    print_result=False
)


# Variables generales
comando = None

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


# FIRESTORE

def abrir_sesion():
    global sesion_id, sesion_ref, lecturas_ref

    try:
        sesion_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        sesion_ref = db.collection("sesiones").document(sesion_id)
        sesion_ref.set({
            "inicio": firestore.SERVER_TIMESTAMP,
            "topic": TOPIC
        })
        lecturas_ref = sesion_ref.collection("lecturas")
        print(">>> Sesión abierta en Firestore:", sesion_id)

    except Exception as e:
        print("Error al abrir la sesión en Firestore:", e)
        sesion_id = sesion_ref = lecturas_ref = None


def cerrar_sesion():
    global sesion_id, sesion_ref, lecturas_ref

    if sesion_ref is not None:
        try:
            sesion_ref.update({"fin": firestore.SERVER_TIMESTAMP})
            print(">>> Sesión cerrada en Firestore:", sesion_id)

        except Exception as e:
            print("Error al cerrar la sesión en Firestore:", e)

    sesion_id = sesion_ref = lecturas_ref = None


def guardar_lectura(aceleracion, giro, voltaje, delta_gsr, bpm):
    if lecturas_ref is None:
        return

    doc = {
        "sesion_id": sesion_id,
        "timestamp": datetime.now(timezone.utc),
        "nodo": NODE_ID,
        "accel_x": round(aceleracion["x"], 4),
        "accel_y": round(aceleracion["y"], 4),
        "accel_z": round(aceleracion["z"], 4),
        "giro_x": round(giro["x"], 4),
        "giro_y": round(giro["y"], 4),
        "giro_z": round(giro["z"], 4),
        "gsr_voltage": round(voltaje, 4),
        "gsr_delta": round(delta_gsr, 4),
    }

    # Derivados (en reposo la magnitud es ~1 g, por eso se resta 1)
    doc["movimiento"] = round(abs(math.sqrt(
        aceleracion["x"] ** 2 +
        aceleracion["y"] ** 2 +
        aceleracion["z"] ** 2
    ) - 1.0), 4)

    doc["giro_total"] = round(math.sqrt(
        giro["x"] ** 2 +
        giro["y"] ** 2 +
        giro["z"] ** 2
    ), 4)

    # bpm = 0 significa "sin pulso detectado"
    if bpm > 0:
        doc["bpm"] = round(bpm, 4)

    try:
        lecturas_ref.add(doc)

    except Exception as e:
        print("Error al guardar en Firestore:", e)


#  MQTT callbacks

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

    global comando

    try:
        comando = json.loads(msg.payload.decode())["comando"]

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


# CALIBRACIÓN DEL IMU

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


# LÍNEA BASE DEL GSR

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


# PROGRAMA PRINCIPAL

calibracion_imu = None

baseline_gsr = 0

midiendo = False

pulso_activo = False


print("\nEsperando comandos...\n")


try:

    while True:

        cmd = comando
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

                calibracion_imu = calibrar_imu()

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

                baseline_gsr = calcular_baseline_gsr()

                hrm.start_sensor()

                pulso_activo = True

                print(
                    f"\nEstabilizando sensor de pulso "
                    f"({SEGUNDOS_ESTABILIZACION_BPM}s)..."
                )

                time.sleep(SEGUNDOS_ESTABILIZACION_BPM)

                midiendo = True

                abrir_sesion()

                publicar_estado("Sesión en curso")


        # FINALIZAR

        elif cmd == "finalizar":

            if midiendo:

                midiendo = False

                hrm.stop_sensor()

                pulso_activo = False

                cerrar_sesion()

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


                # Mensaje SenML (para ThingsBoard)

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

                # Guarda la misma lectura en Firestore
                guardar_lectura(aceleracion, giro, voltaje, delta_gsr, bpm)

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

    # Cierra la sesión de Firestore si quedó abierta
    cerrar_sesion()

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