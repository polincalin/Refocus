#!/usr/bin/env python3

import time
import json
import ssl

from mpu6050 import mpu6050
import board
import busio
import adafruit_ads1x15.ads1115 as ADS
from adafruit_ads1x15.analog_in import AnalogIn

from heartrate_monitor import HeartRateMonitor
import paho.mqtt.client as mqtt

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


# Sensores

imu = mpu6050(dirIMU)

i2c = busio.I2C(
    board.SCL,
    board.SDA
)

ADC = ADS.ADS1115(
    i2c,
    address=dirGSR
)

GSR = AnalogIn(
    ADC,
    0
)

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
        json.dumps({
            "estado": texto
        }),
        retain=True
    )


#  MQTT callbacks

def on_connect(client, userdata, flags, rc):

    if rc == 0:

        print(
            ">>> CONEXIÓN EXITOSA: "
            "El broker EMQX aceptó la conexión."
        )

        client.subscribe(
            TOPIC_COMANDOS
        )

        publicar_estado(
            estado_actual
        )

    elif rc == 1:

        print(
            ">>> ERROR: "
            "Versión de protocolo incorrecta."
        )

    elif rc == 4:

        print(
            ">>> ERROR: "
            "Usuario o contraseña rechazados por EMQX."
        )

    elif rc == 5:

        print(
            ">>> ERROR: "
            "Cliente no autorizado."
        )

    else:

        print(
            f">>> ERROR DE CONEXIÓN. Código rc={rc}"
        )


def on_message(client, userdata, msg):

    global comando

    try:

        comando = json.loads(
            msg.payload.decode()
        )["comando"]

    except Exception:

        print(
            ">>> Comando inválido:",
            msg.payload
        )


# Cliente MQTT

mqtt_client = mqtt.Client(
    client_id=NODE_ID
)

mqtt_client.username_pw_set(
    MQTT_USER,
    MQTT_PASS
)

mqtt_client.tls_set(
    tls_version=ssl.PROTOCOL_TLS
)


# Si la Raspberry se apaga inesperadamente,
# EMQX publica este estado.
mqtt_client.will_set(
    TOPIC_ESTADO,
    json.dumps({
        "estado": "Wearable desconectado"
    }),
    retain=True
)


mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message


print(
    "Conectando al broker EMQX Cloud..."
)

mqtt_client.connect(
    BROKER,
    PORT,
    keepalive=60
)

mqtt_client.loop_start()


# CALIBRACIÓN DEL IMU


def calibrar_imu():

    print(
        "\nCalibrando IMU: "
        "el sensor debe estar QUIETO y PLANO sobre una mesa."
    )

    suma_accel = {
        "x": 0,
        "y": 0,
        "z": 0
    }

    suma_giro = {
        "x": 0,
        "y": 0,
        "z": 0
    }


    for _ in range(
        MUESTRAS_CALIBRACION_IMU
    ):

        a = imu.get_accel_data(
            g=True
        )

        g = imu.get_gyro_data()


        for eje in (
            "x",
            "y",
            "z"
        ):

            suma_accel[eje] += a[eje]
            suma_giro[eje] += g[eje]


        time.sleep(
            0.01
        )


    offset_accel = {
        eje:
            suma_accel[eje] /
            MUESTRAS_CALIBRACION_IMU

        for eje in "xyz"
    }


    offset_giro = {
        eje:
            suma_giro[eje] /
            MUESTRAS_CALIBRACION_IMU

        for eje in "xyz"
    }


    # En reposo Z debe marcar aproximadamente 1 g
    offset_accel["z"] -= 1.0


    print(
        "Calibración del IMU completada."
    )


    return {
        "accel": offset_accel,
        "giro": offset_giro
    }



# LÍNEA BASE DEL GSR

def calcular_baseline_gsr():

    print(
        f"\nCalculando línea base de GSR: "
        f"mantente en reposo "
        f"{MUESTRAS_BASELINE_GSR} segundos..."
    )


    suma = 0


    for i in range(
        MUESTRAS_BASELINE_GSR
    ):

        suma += GSR.voltage

        time.sleep(
            1
        )

        print(
            f"{i + 1}/"
            f"{MUESTRAS_BASELINE_GSR}s",
            end="\r"
        )


    baseline = (
        suma /
        MUESTRAS_BASELINE_GSR
    )


    print(
        f"\nLínea base de GSR: "
        f"{baseline:.3f} V"
    )


    return baseline



# PROGRAMA PRINCIPAL

calibracion_imu = None

baseline_gsr = 0

midiendo = False

pulso_activo = False


print(
    "\nEsperando comandos...\n"
)


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
                print(
                    ">>> Finaliza la sesión "
                    "antes de calibrar"
                )

            else:

                publicar_estado(
                    "Calibrando: "
                    "mantén el sensor quieto y plano"
                )

                calibracion_imu = (
                    calibrar_imu()
                )

                publicar_estado(
                    "Calibración lista"
                )


        # INICIAR

        elif cmd == "iniciar":

            if calibracion_imu is None:

                publicar_estado(
                    "Primero calibra el sensor"
                )

            elif not midiendo:

                publicar_estado(
                    f"Preparando sesión: "
                    f"quédate en reposo "
                    f"{MUESTRAS_BASELINE_GSR} s"
                )


                baseline_gsr = (
                    calcular_baseline_gsr()
                )


                hrm.start_sensor()

                pulso_activo = True


                print(
                    f"\nEstabilizando sensor de pulso "
                    f"({SEGUNDOS_ESTABILIZACION_BPM}s)..."
                )


                time.sleep(
                    SEGUNDOS_ESTABILIZACION_BPM
                )


                midiendo = True


                publicar_estado(
                    "Sesión en curso"
                )


        # FINALIZAR

        elif cmd == "finalizar":

            if midiendo:

                midiendo = False


                hrm.stop_sensor()

                pulso_activo = False


                publicar_estado(
                    "Sesión finalizada"
                )



        # LECTURA DE SENSORES
  

        if midiendo:

            try:

                aceleracion_cruda = (
                    imu.get_accel_data(
                        g=True
                    )
                )


                giro_crudo = (
                    imu.get_gyro_data()
                )


                voltaje = (
                    GSR.voltage
                )


                # Aplica calibración del IMU
                aceleracion = {

                    eje:
                        aceleracion_cruda[eje]
                        -
                        calibracion_imu[
                            "accel"
                        ][eje]

                    for eje in "xyz"
                }


                giro = {

                    eje:
                        giro_crudo[eje]
                        -
                        calibracion_imu[
                            "giro"
                        ][eje]

                    for eje in "xyz"
                }


                delta_gsr = (
                    voltaje -
                    baseline_gsr
                )


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

                    f"GSR: "
                    f"{voltaje:.3f} V "
                    f"(Δ{delta_gsr:+.3f}) | "

                    f"Pulso: "
                    f"{bpm:.1f} BPM"
                )


                # Mensaje

                mensaje_senml = [

                    {
                        "bn":
                            f"urn:dev:refocus:"
                            f"{NODE_ID}:",

                        "bt":
                            time.time(),

                        "n":
                            "identity1",

                        "vs":
                            "identity1"
                    },

                    {
                        "n": "accel_x",
                        "u": "g",
                        "v": aceleracion["x"]
                    },

                    {
                        "n": "accel_y",
                        "u": "g",
                        "v": aceleracion["y"]
                    },

                    {
                        "n": "accel_z",
                        "u": "g",
                        "v": aceleracion["z"]
                    },

                    {
                        "n": "giro_x",
                        "u": "deg/s",
                        "v": giro["x"]
                    },

                    {
                        "n": "giro_y",
                        "u": "deg/s",
                        "v": giro["y"]
                    },

                    {
                        "n": "giro_z",
                        "u": "deg/s",
                        "v": giro["z"]
                    },

                    {
                        "n": "gsr_voltage",
                        "u": "V",
                        "v": voltaje
                    },

                    {
                        "n": "gsr_delta",
                        "u": "V",
                        "v": delta_gsr
                    },

                    {
                        "n": "bpm",
                        "u": "bpm",
                        "v": bpm
                    }
                ]


                mqtt_client.publish(
                    TOPIC,
                    json.dumps(
                        mensaje_senml
                    )
                )


                time.sleep(
                    1
                )


            except OSError as e:

                if e.errno == 121:

                    time.sleep(
                        0.05
                    )

                    continue

                else:

                    raise


        else:

            time.sleep(
                0.1
            )


except KeyboardInterrupt:

    print(
        "\nPrograma detenido."
    )


finally:

    if pulso_activo:

        hrm.stop_sensor()


    try:

        info = mqtt_client.publish(
            TOPIC_ESTADO,

            json.dumps({
                "estado":
                    "Wearable desconectado"
            }),

            retain=True
        )


        try:

            info.wait_for_publish(
                timeout=3
            )

        except TypeError:

            time.sleep(
                0.5
            )


    except Exception as e:

        print(
            "No se pudo avisar "
            "la desconexión:",
            e
        )


    mqtt_client.loop_stop()

    mqtt_client.disconnect()


    print(
        "Conexión MQTT cerrada."
    )