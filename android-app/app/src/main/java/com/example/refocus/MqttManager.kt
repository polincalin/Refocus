package com.example.refocus

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

object MqttManager {

    // --------------------------------
    // CONFIGURACIÓN DEL BROKER
    // --------------------------------

    private const val HOST =
        "ab111316.ala.us-east-1.emqxsl.com"

    private const val PORT =
        8883

    // Para esta primera prueba usa las mismas
    // credenciales MQTT que tiene el dashboard web.
    //
    // NO las subamos todavía a GitHub.
    private const val USERNAME =
        "web"

    private const val PASSWORD =
        "web"

    // Deben coincidir con la Raspberry
    private const val TOPIC_COMANDOS =
        "comandos"

    private const val TOPIC_ESTADO =
        "estado"

    // --------------------------------
    // CLIENTE MQTT
    // --------------------------------

    private var client: Mqtt5AsyncClient? = null

    private var conectado = false

    private val mainHandler =
        Handler(Looper.getMainLooper())

    // --------------------------------
    // CALLBACKS PARA LA APP
    // --------------------------------

    var onConexionCambiada:
            ((Boolean) -> Unit)? = null

    var onEstadoRecibido:
            ((String) -> Unit)? = null

    // --------------------------------
    // CONECTAR
    // --------------------------------

    fun conectar() {

        if (conectado) {
            return
        }

        val nuevoCliente =
            Mqtt5Client
                .builder()

                // Cada celular tendrá un ID MQTT diferente
                .identifier(
                    "refocus-android-" +
                            UUID.randomUUID().toString()
                )

                .serverHost(HOST)

                .serverPort(PORT)

                // MQTT seguro
                .sslWithDefaultConfig()

                // Usuario y contraseña MQTT
                .simpleAuth()
                .username(USERNAME)
                .password(
                    PASSWORD.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                .applySimpleAuth()

                .buildAsync()

        client = nuevoCliente

        nuevoCliente
            .connect()
            .whenComplete { _, error ->

                if (error != null) {

                    conectado = false

                    Log.e(
                        "REFocusMQTT",
                        "Error conectando a MQTT",
                        error
                    )

                    mainHandler.post {

                        onConexionCambiada
                            ?.invoke(false)
                    }

                } else {

                    conectado = true

                    Log.d(
                        "REFocusMQTT",
                        "Conectado a EMQX"
                    )

                    mainHandler.post {

                        onConexionCambiada
                            ?.invoke(true)
                    }

                    suscribirseAEstado()
                }
            }
    }

    // --------------------------------
    // ESCUCHAR ESTADO DE RASPBERRY
    // --------------------------------

    private fun suscribirseAEstado() {

        val mqttClient =
            client ?: return

        mqttClient
            .subscribeWith()

            .topicFilter(
                TOPIC_ESTADO
            )

            .qos(
                MqttQos.AT_LEAST_ONCE
            )

            .callback { publish ->

                try {

                    val buffer =
                        publish.payload.orElse(null)

                    if (buffer == null) {
                        return@callback
                    }

                    val mensaje =
                        StandardCharsets.UTF_8
                            .decode(buffer)
                            .toString()

                    Log.d(
                        "REFocusMQTT",
                        "Estado recibido: $mensaje"
                    )

                    val estado = try {

                        val json =
                            JSONObject(mensaje)

                        json.optString(
                            "estado",
                            mensaje
                        )

                    } catch (_: Exception) {

                        mensaje
                    }

                    mainHandler.post {

                        onEstadoRecibido
                            ?.invoke(estado)
                    }

                } catch (e: Exception) {

                    Log.e(
                        "REFocusMQTT",
                        "Error leyendo estado",
                        e
                    )
                }
            }

            .send()
            .whenComplete { _, error ->

                if (error != null) {

                    Log.e(
                        "REFocusMQTT",
                        "Error suscribiendo a estado",
                        error
                    )

                } else {

                    Log.d(
                        "REFocusMQTT",
                        "Suscrito al tópico estado"
                    )
                }
            }
    }

    // --------------------------------
    // ENVIAR COMANDO
    // --------------------------------

    fun enviarComando(
        comando: String,
        onResult: (Boolean) -> Unit = {}
    ) {

        if (!conectado) {

            Log.e(
                "REFocusMQTT",
                "No conectado. No se puede enviar $comando"
            )

            mainHandler.post {
                onResult(false)
            }

            return
        }

        val mqttClient =
            client ?: return

        val mensaje =
            JSONObject()
                .put(
                    "comando",
                    comando
                )
                .toString()

        mqttClient
            .publishWith()

            .topic(
                TOPIC_COMANDOS
            )

            .qos(
                MqttQos.AT_LEAST_ONCE
            )

            .payload(
                mensaje.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

            .send()
            .whenComplete { _, error ->

                if (error != null) {

                    Log.e(
                        "REFocusMQTT",
                        "Error enviando comando $comando",
                        error
                    )

                    mainHandler.post {
                        onResult(false)
                    }

                } else {

                    Log.d(
                        "REFocusMQTT",
                        "Comando enviado: $mensaje"
                    )

                    mainHandler.post {
                        onResult(true)
                    }
                }
            }
    }

    // --------------------------------
    // DESCONECTAR
    // --------------------------------

    fun desconectar() {

        client
            ?.disconnect()
            ?.whenComplete { _, _ ->

                conectado = false

                mainHandler.post {

                    onConexionCambiada
                        ?.invoke(false)
                }
            }
    }

    fun estaConectado(): Boolean {

        return conectado
    }
}