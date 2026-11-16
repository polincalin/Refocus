package com.example.refocus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun TipsScreen(
    onHomeClick: () -> Unit,
    onStatsClick: () -> Unit,
    onHabitsClick: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var cargandoAnalitica by remember { mutableStateOf(true) }
    var cargandoDigital by remember { mutableStateOf(true) }

    var sesionesAnaliticas by remember {
        mutableStateOf<List<AnalyticsSession>>(emptyList())
    }

    var sesionesDigitales by remember {
        mutableStateOf<List<DigitalSession>>(emptyList())
    }

    LaunchedEffect(uid) {
        if (uid == null) {
            cargandoAnalitica = false
            cargandoDigital = false
        } else {
            AnalyticsRepository.cargarSesionesUsuario(
                uid = uid,
                onSuccess = {
                    sesionesAnaliticas = it
                    cargandoAnalitica = false
                },
                onError = {
                    cargandoAnalitica = false
                }
            )

            AnalyticsRepository.cargarSesionesDigitalesUsuario(
                uid = uid,
                onSuccess = {
                    sesionesDigitales = it
                    cargandoDigital = false
                },
                onError = {
                    cargandoDigital = false
                }
            )
        }
    }

    val cargando = cargandoAnalitica || cargandoDigital
    val consejos = crearConsejos(
        sesionesAnaliticas,
        sesionesDigitales
    )

    Surface(
        Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 12.dp)
        ) {
            Text(
                "Consejos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Text(
                "Sugerencias sencillas según tus sesiones recientes.",
                color = Color(0xFF686878)
            )

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (cargando) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF6755CE)
                        )
                    }
                } else {
                    consejos.forEachIndexed { index, consejo ->
                        TipCard(index + 1, consejo)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            ReFocusBottomBar(
                selected = "tips",
                onHome = onHomeClick,
                onStats = onStatsClick,
                onHabits = onHabitsClick,
                onTips = {}
            )
        }
    }
}

@Composable
private fun TipCard(
    numero: Int,
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Consejo $numero",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6755CE)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text,
                color = Color(0xFF1D427A)
            )
        }
    }
}

private fun crearConsejos(
    sesionesAnaliticas: List<AnalyticsSession>,
    sesionesDigitales: List<DigitalSession>
): List<String> {
    if (sesionesAnaliticas.isEmpty() && sesionesDigitales.isEmpty()) {
        return listOf(
            "Empieza con una sesión corta y sin notificaciones para crear una línea base.",
            "Mantén el celular solo para lo necesario durante la sesión.",
            "Haz pausas breves entre bloques de estudio para volver con más claridad."
        )
    }

    val resultado = mutableListOf<String>()

    // ---------------------------------------------------------
    // INTERRUPCIONES DIGITALES
    // ---------------------------------------------------------

    val digitalesRecientes = sesionesDigitales.take(5)

    if (digitalesRecientes.isNotEmpty()) {
        val promedioCambios = digitalesRecientes
            .map { it.interrupcionesDigitales }
            .average()

        when {
            promedioCambios >= 3.0 -> {
                resultado += "Has cambiado de aplicación varias veces durante tus sesiones. Prueba silenciar notificaciones y dejar abiertas solo las herramientas que necesitas para estudiar."
            }

            promedioCambios >= 1.0 -> {
                resultado += "A veces sales de ReFocus durante tus sesiones. Intenta reducir cambios de aplicación para mantener un bloque de estudio más continuo."
            }

            else -> {
                resultado += "Casi no cambias de aplicación durante tus sesiones recientes. Mantén ese hábito para reducir interrupciones digitales."
            }
        }
    }

    // ---------------------------------------------------------
    // ANALÍTICA DEL WEARABLE
    // ---------------------------------------------------------

    val recientes = sesionesAnaliticas.take(5)

    if (recientes.isNotEmpty()) {
        val promedioDistracciones = recientes
            .map { it.nDistracciones }
            .average()

        val promedioDuracion = recientes
            .map { it.duracionS }
            .average()

        val movimientos = recientes.mapNotNull {
            it.pctMovAlto
        }

        val promedioMovimiento = if (movimientos.isEmpty()) {
            null
        } else {
            movimientos.average()
        }

        if (promedioDistracciones >= 3.0) {
            resultado += "En tus sesiones recientes se detectaron varias posibles distracciones. Prueba bloques de estudio más cortos y una pausa programada entre ellos."
        } else {
            resultado += "Tus sesiones recientes tienen pocas posibles distracciones detectadas. Intenta mantener el mismo entorno y rutina de estudio."
        }

        when {
            promedioDuracion >= 45 * 60 -> {
                resultado += "Tus sesiones suelen ser largas. Una pausa corta cada 40–50 minutos puede ayudarte a mantener el ritmo."
            }

            promedioMovimiento != null && promedioMovimiento > 20.0 -> {
                resultado += "Se registró bastante movimiento en tus sesiones recientes. Busca una postura y superficie de estudio más estables."
            }

            else -> {
                resultado += "Revisa tus hábitos después de varias sesiones para identificar qué condiciones te ayudan a estudiar con menos interrupciones."
            }
        }
    }

    while (resultado.size < 3) {
        resultado += "Antes de empezar una sesión, define un objetivo concreto y deja a mano solo lo que necesitas."
    }

    return resultado.take(3)
}
