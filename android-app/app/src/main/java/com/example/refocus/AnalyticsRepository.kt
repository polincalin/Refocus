package com.example.refocus

import com.google.firebase.firestore.FirebaseFirestore

data class AnalyticsSession(
    val id: String,
    val inicioMillis: Long,
    val duracionS: Int,
    val nDistracciones: Int,
    val nAlertas: Int,
    val tasaEnfoque: Double?,
    val pctMovAlto: Double?,
    val bpmProm: Double?
)

data class DigitalSession(
    val id: String,
    val inicioMillis: Long,
    val duracionS: Int,
    val interrupcionesDigitales: Int
)

object AnalyticsRepository {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    // Analítica generada por la Raspberry en la colección raíz "sesiones".
    fun cargarSesionesUsuario(
        uid: String,
        onSuccess: (List<AnalyticsSession>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("sesiones")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val sesiones = snapshot.documents
                    .mapNotNull { doc ->
                        val inicio = doc.getTimestamp("inicio")?.toDate()?.time
                            ?: return@mapNotNull null

                        // Solo usamos sesiones que ya fueron cerradas por la Raspberry.
                        if (doc.getTimestamp("fin") == null) {
                            return@mapNotNull null
                        }

                        AnalyticsSession(
                            id = doc.id,
                            inicioMillis = inicio,
                            duracionS = (doc.getLong("duracion_s") ?: 0L).toInt(),
                            nDistracciones = (doc.getLong("n_distracciones") ?: 0L).toInt(),
                            nAlertas = (doc.getLong("n_alertas") ?: 0L).toInt(),
                            tasaEnfoque = doc.getDouble("tasa_enfoque"),
                            pctMovAlto = doc.getDouble("pct_mov_alto"),
                            bpmProm = doc.getDouble("bpm_prom")
                        )
                    }
                    .sortedByDescending { it.inicioMillis }

                onSuccess(sesiones)
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }

    // Sesiones del usuario creadas por la app/web. Aquí se guardan los cambios de app.
    fun cargarSesionesDigitalesUsuario(
        uid: String,
        onSuccess: (List<DigitalSession>) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        db.collection("usuarios")
            .document(uid)
            .collection("sesiones")
            .get()
            .addOnSuccessListener { snapshot ->
                val sesiones = snapshot.documents
                    .mapNotNull { doc ->
                        if (doc.getString("estado") != "finalizada") {
                            return@mapNotNull null
                        }

                        val inicio = doc.getLong("startTs")
                            ?: return@mapNotNull null

                        DigitalSession(
                            id = doc.id,
                            inicioMillis = inicio,
                            duracionS = (doc.getLong("duracionSegundos") ?: 0L).toInt(),
                            interrupcionesDigitales =
                                (doc.getLong("interrupcionesDigitales") ?: 0L).toInt()
                        )
                    }
                    .sortedByDescending { it.inicioMillis }

                onSuccess(sesiones)
            }
            .addOnFailureListener { error ->
                onError(error)
            }
    }
}
