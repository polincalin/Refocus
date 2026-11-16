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

    private const val HOST = "ab111316.ala.us-east-1.emqxsl.com"
    private const val PORT = 8883

    // Mantener estas credenciales fuera del repositorio público final.
    private const val USERNAME = "web"
    private const val PASSWORD = "web"

    private const val TOPIC_COMANDOS = "comandos"
    private const val TOPIC_ESTADO = "estado"
    private const val TOPIC_ALERTAS = "alertas"

    private var client: Mqtt5AsyncClient? = null
    private var conectado = false

    private val mainHandler = Handler(Looper.getMainLooper())

    var onConexionCambiada: ((Boolean) -> Unit)? = null
    var onEstadoRecibido: ((String) -> Unit)? = null

    // Entrega el UID incluido en la alerta para que la app pueda
    // comprobar que la vibración pertenece al usuario actual.
    var onAlertaRecibida: ((String?) -> Unit)? = null

    fun conectar() {
        if (conectado) return

        val nuevoCliente = Mqtt5Client.builder()
            .identifier("refocus-android-${UUID.randomUUID()}")
            .serverHost(HOST)
            .serverPort(PORT)
            .sslWithDefaultConfig()
            .simpleAuth()
            .username(USERNAME)
            .password(PASSWORD.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .buildAsync()

        client = nuevoCliente

        nuevoCliente.connect().whenComplete { _, error ->
            if (error != null) {
                conectado = false
                Log.e("REFocusMQTT", "Error conectando a MQTT", error)
                mainHandler.post { onConexionCambiada?.invoke(false) }
            } else {
                conectado = true
                Log.d("REFocusMQTT", "Conectado a EMQX")
                mainHandler.post { onConexionCambiada?.invoke(true) }
                suscribirseAEstado()
                suscribirseAAlertas()
            }
        }
    }

    private fun suscribirseAEstado() {
        val mqttClient = client ?: return

        mqttClient.subscribeWith()
            .topicFilter(TOPIC_ESTADO)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                try {
                    val buffer = publish.payload.orElse(null) ?: return@callback
                    val mensaje = StandardCharsets.UTF_8.decode(buffer).toString()

                    val estado = try {
                        JSONObject(mensaje).optString("estado", mensaje)
                    } catch (_: Exception) {
                        mensaje
                    }

                    mainHandler.post {
                        onEstadoRecibido?.invoke(estado)
                    }
                } catch (e: Exception) {
                    Log.e("REFocusMQTT", "Error leyendo estado", e)
                }
            }
            .send()
    }

    private fun suscribirseAAlertas() {
        val mqttClient = client ?: return

        mqttClient.subscribeWith()
            .topicFilter(TOPIC_ALERTAS)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                try {
                    val buffer = publish.payload.orElse(null) ?: return@callback
                    val mensaje = StandardCharsets.UTF_8.decode(buffer).toString()
                    val json = JSONObject(mensaje)

                    if (json.optString("alerta") == "vibracion") {
                        val uid = json.optString("uid").takeIf { it.isNotBlank() && it != "null" }
                        mainHandler.post {
                            onAlertaRecibida?.invoke(uid)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("REFocusMQTT", "Error leyendo alerta", e)
                }
            }
            .send()
    }

    fun enviarComando(
        comando: String,
        uid: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (!conectado) {
            mainHandler.post { onResult(false) }
            return
        }

        val mqttClient = client ?: run {
            mainHandler.post { onResult(false) }
            return
        }

        val json = JSONObject().put("comando", comando)
        if (!uid.isNullOrBlank()) {
            json.put("uid", uid)
        }

        val mensaje = json.toString()

        mqttClient.publishWith()
            .topic(TOPIC_COMANDOS)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(mensaje.toByteArray(StandardCharsets.UTF_8))
            .send()
            .whenComplete { _, error ->
                mainHandler.post {
                    onResult(error == null)
                }
            }
    }

    fun desconectar() {
        client?.disconnect()?.whenComplete { _, _ ->
            conectado = false
            mainHandler.post {
                onConexionCambiada?.invoke(false)
            }
        }
    }

    fun estaConectado(): Boolean = conectado
}
