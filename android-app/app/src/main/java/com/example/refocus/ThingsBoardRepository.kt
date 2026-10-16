package com.example.refocus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TelemetryPoint(
    val ts: Long,
    val value: Float
)

data class ThingsBoardStats(
    val bpm: List<TelemetryPoint> = emptyList(),
    val gsr: List<TelemetryPoint> = emptyList(),
    val movimiento: List<TelemetryPoint> = emptyList()
)

object ThingsBoardRepository {

    private const val BASE_URL =
        "https://thingsboard.cloud"

    private const val PUBLIC_ID =
        "755de5d0-c31a-11f1-b930-ad60e0953111"

    private const val DEVICE_ID =
        "cd235bf0-bbb4-11f1-85db-879f857253db"

    // =========================================================
    // CARGAR LOS DATOS MÁS RECIENTES
    // =========================================================

    suspend fun cargarUltimosSieteDias():
            Result<ThingsBoardStats> =
        withContext(Dispatchers.IO) {

            runCatching {

                val token =
                    obtenerTokenPublico()

                val endTs =
                    System.currentTimeMillis()

                val startTs =
                    endTs -
                            (7L *
                                    24L *
                                    60L *
                                    60L *
                                    1000L)

                val json =
                    obtenerTelemetria(
                        token = token,
                        startTs = startTs,
                        endTs = endTs
                    )

                ThingsBoardStats(
                    bpm =
                        leerSerie(
                            json,
                            "bpm"
                        ),

                    gsr =
                        leerSerie(
                            json,
                            "gsr_voltage"
                        ),

                    movimiento =
                        leerSerie(
                            json,
                            "movimiento"
                        )
                )
            }
        }

    // =========================================================
    // LOGIN PÚBLICO DE THINGSBOARD
    // =========================================================

    private fun obtenerTokenPublico(): String {

        val url =
            URL(
                "$BASE_URL/api/auth/login/public"
            )

        val conexion =
            url.openConnection()
                    as HttpURLConnection

        try {

            conexion.requestMethod =
                "POST"

            conexion.connectTimeout =
                10000

            conexion.readTimeout =
                15000

            conexion.doOutput =
                true

            conexion.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            val body =
                JSONObject()
                    .put(
                        "publicId",
                        PUBLIC_ID
                    )
                    .toString()

            conexion.outputStream.use {
                    output ->

                output.write(
                    body.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            val respuesta =
                leerRespuesta(conexion)

            val json =
                JSONObject(respuesta)

            return json.getString(
                "token"
            )

        } finally {

            conexion.disconnect()
        }
    }

    // =========================================================
    // DESCARGAR TELEMETRÍA
    // =========================================================

    private fun obtenerTelemetria(
        token: String,
        startTs: Long,
        endTs: Long
    ): JSONObject {

        val keys =
            "bpm,gsr_voltage,movimiento"

        val direccion =
            "$BASE_URL/api/plugins/telemetry/" +
                    "DEVICE/$DEVICE_ID/" +
                    "values/timeseries" +
                    "?keys=$keys" +
                    "&startTs=$startTs" +
                    "&endTs=$endTs" +
                    "&limit=500" +
                    "&agg=NONE" +
                    "&orderBy=DESC"

        val conexion =
            URL(direccion)
                .openConnection()
                    as HttpURLConnection

        try {

            conexion.requestMethod =
                "GET"

            conexion.connectTimeout =
                10000

            conexion.readTimeout =
                15000

            conexion.setRequestProperty(
                "Accept",
                "application/json"
            )

            conexion.setRequestProperty(
                "X-Authorization",
                "Bearer $token"
            )

            val respuesta =
                leerRespuesta(conexion)

            return JSONObject(
                respuesta
            )

        } finally {

            conexion.disconnect()
        }
    }

    // =========================================================
    // LEER UNA SERIE DE THINGSBOARD
    // =========================================================

    private fun leerSerie(
        json: JSONObject,
        key: String
    ): List<TelemetryPoint> {

        val array =
            json.optJSONArray(key)
                ?: return emptyList()

        val puntos =
            mutableListOf<TelemetryPoint>()

        for (
        i in 0 until array.length()
        ) {

            val elemento =
                array.optJSONObject(i)
                    ?: continue

            val ts =
                elemento.optLong(
                    "ts",
                    -1L
                )

            val value =
                elemento
                    .opt("value")
                    ?.toString()
                    ?.toFloatOrNull()

            if (
                ts > 0 &&
                value != null &&
                value.isFinite()
            ) {

                puntos.add(
                    TelemetryPoint(
                        ts = ts,
                        value = value
                    )
                )
            }
        }

        return puntos
            .sortedBy {
                it.ts
            }
    }

    // =========================================================
    // LEER RESPUESTA HTTP
    // =========================================================

    private fun leerRespuesta(
        conexion: HttpURLConnection
    ): String {

        val codigo =
            conexion.responseCode

        val stream =
            if (
                codigo in 200..299
            ) {

                conexion.inputStream

            } else {

                conexion.errorStream
            }

        val texto =
            stream
                ?.bufferedReader()
                ?.use {
                    it.readText()
                }
                .orEmpty()

        if (
            codigo !in 200..299
        ) {

            throw IllegalStateException(
                "ThingsBoard respondió HTTP " +
                        "$codigo: $texto"
            )
        }

        return texto
    }
}