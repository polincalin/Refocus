package com.example.refocus

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

data class HomeSessionStats(
    val tiempoTotalSegundos: Int = 0,
    val cantidadSesiones: Int = 0,
    val duracionUltimaSesion: Int = 0,
    val hayUltimaSesion: Boolean = false
)

object SessionRepository {

    private const val TAG = "REFocusFirestore"

    private val db by lazy {
        Log.d(TAG, "FirebaseFirestore.getInstance() creado")
        FirebaseFirestore.getInstance()
    }

    // =========================================================
    // CREAR UNA SESIÓN
    // =========================================================

    fun crearSesion(
        uid: String,
        sessionId: String,
        startTs: Long,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {

        Log.d(
            TAG,
            "Intentando CREAR sesión. UID=$uid, sessionId=$sessionId"
        )

        val datos = hashMapOf<String, Any>(
            "uid" to uid,
            "startTs" to startTs,
            "duracionSegundos" to 0L,
            "estado" to "activa",
            "origen" to "android",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("usuarios")
            .document(uid)
            .collection("sesiones")
            .document(sessionId)
            .set(datos)
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "✅ Sesión creada correctamente en Firestore"
                )

                onSuccess()
            }
            .addOnFailureListener { exception ->

                Log.e(
                    TAG,
                    "❌ ERROR creando sesión: ${exception.message}",
                    exception
                )

                onError(exception)
            }
    }

    // =========================================================
    // FINALIZAR UNA SESIÓN
    // =========================================================

    fun finalizarSesion(
        uid: String,
        sessionId: String,
        endTs: Long,
        duracionSegundos: Int,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {

        Log.d(
            TAG,
            "Intentando FINALIZAR sesión. UID=$uid, sessionId=$sessionId"
        )

        Log.d(
            TAG,
            "Duración de la sesión: $duracionSegundos segundos"
        )

        val datos = hashMapOf<String, Any>(
            "endTs" to endTs,
            "duracionSegundos" to duracionSegundos.toLong(),
            "estado" to "finalizada",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("usuarios")
            .document(uid)
            .collection("sesiones")
            .document(sessionId)
            .set(
                datos,
                SetOptions.merge()
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "✅ Sesión finalizada correctamente en Firestore"
                )

                onSuccess()
            }
            .addOnFailureListener { exception ->

                Log.e(
                    TAG,
                    "❌ ERROR finalizando sesión: ${exception.message}",
                    exception
                )

                onError(exception)
            }
    }

    // =========================================================
    // ESCUCHAR DATOS PARA EL HOME
    // =========================================================

    fun escucharHome(
        uid: String,
        onUpdate: (HomeSessionStats) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "🔥 Home está intentando usar Firestore"
        )

        Log.d(
            TAG,
            "UID del usuario: $uid"
        )

        Log.d(
            TAG,
            "Ruta: usuarios/$uid/sesiones"
        )

        Log.d(
            TAG,
            "========================================"
        )

        return db.collection("usuarios")
            .document(uid)
            .collection("sesiones")
            .addSnapshotListener { snapshot, error ->

                // -------------------------------------------------
                // ERROR
                // -------------------------------------------------

                if (error != null) {

                    Log.e(
                        TAG,
                        "❌ ERROR leyendo Firestore: ${error.message}",
                        error
                    )

                    onError(error)

                    return@addSnapshotListener
                }

                // -------------------------------------------------
                // FIRESTORE RESPONDIÓ
                // -------------------------------------------------

                val cantidadDocumentos =
                    snapshot?.size() ?: 0

                Log.d(
                    TAG,
                    "✅ Firestore respondió correctamente"
                )

                Log.d(
                    TAG,
                    "Documentos encontrados: $cantidadDocumentos"
                )

                // -------------------------------------------------
                // OBTENER DOCUMENTOS
                // -------------------------------------------------

                val documentos =
                    snapshot?.documents.orEmpty()

                documentos.forEach { documento ->

                    Log.d(
                        TAG,
                        "Documento encontrado: ${documento.id}"
                    )

                    Log.d(
                        TAG,
                        "Estado: ${documento.getString("estado")}"
                    )

                    Log.d(
                        TAG,
                        "Duración: ${documento.getLong("duracionSegundos")}"
                    )
                }

                // -------------------------------------------------
                // SOLO SESIONES FINALIZADAS
                // -------------------------------------------------

                val finalizadas =
                    documentos.filter {

                        it.getString("estado") ==
                                "finalizada"
                    }

                Log.d(
                    TAG,
                    "Sesiones finalizadas encontradas: ${finalizadas.size}"
                )

                // -------------------------------------------------
                // ÚLTIMA SESIÓN
                // -------------------------------------------------

                val ultima =
                    finalizadas.maxByOrNull {

                        it.getLong("startTs")
                            ?: 0L
                    }

                val duracionUltima =
                    (
                            ultima?.getLong(
                                "duracionSegundos"
                            )
                                ?: 0L
                            ).toInt()

                // -------------------------------------------------
                // INICIO DE ESTA SEMANA
                // -------------------------------------------------

                val inicioSemana =
                    obtenerInicioSemana()

                // -------------------------------------------------
                // SESIONES DE ESTA SEMANA
                // -------------------------------------------------

                val sesionesSemana =
                    finalizadas.filter {

                        (
                                it.getLong("startTs")
                                    ?: 0L
                                ) >= inicioSemana
                    }

                // -------------------------------------------------
                // SUMAR TIEMPO DE LA SEMANA
                // -------------------------------------------------

                val tiempoTotal =
                    sesionesSemana.sumOf {

                        it.getLong(
                            "duracionSegundos"
                        )
                            ?: 0L
                    }

                Log.d(
                    TAG,
                    "Sesiones esta semana: ${sesionesSemana.size}"
                )

                Log.d(
                    TAG,
                    "Tiempo total esta semana: $tiempoTotal segundos"
                )

                Log.d(
                    TAG,
                    "Duración última sesión: $duracionUltima segundos"
                )

                // -------------------------------------------------
                // ENVIAR DATOS AL HOME
                // -------------------------------------------------

                onUpdate(
                    HomeSessionStats(

                        tiempoTotalSegundos =
                            tiempoTotal
                                .coerceAtMost(
                                    Int.MAX_VALUE.toLong()
                                )
                                .toInt(),

                        cantidadSesiones =
                            sesionesSemana.size,

                        duracionUltimaSesion =
                            duracionUltima,

                        hayUltimaSesion =
                            ultima != null
                    )
                )
            }
    }

    // =========================================================
    // CALCULAR INICIO DE SEMANA
    // =========================================================

    private fun obtenerInicioSemana(): Long {

        val calendario =
            Calendar.getInstance()

        while (
            calendario.get(
                Calendar.DAY_OF_WEEK
            ) != Calendar.MONDAY
        ) {

            calendario.add(
                Calendar.DAY_OF_MONTH,
                -1
            )
        }

        calendario.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        calendario.set(
            Calendar.MINUTE,
            0
        )

        calendario.set(
            Calendar.SECOND,
            0
        )

        calendario.set(
            Calendar.MILLISECOND,
            0
        )

        return calendario.timeInMillis
    }
}