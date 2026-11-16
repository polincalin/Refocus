package com.example.refocus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HabitsScreen(
    onHomeClick: () -> Unit,
    onStatsClick: () -> Unit,
    onTipsClick: () -> Unit
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

    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid == null) {
            cargandoAnalitica = false
            cargandoDigital = false
            error = "No se encontró el usuario."
        } else {
            AnalyticsRepository.cargarSesionesUsuario(
                uid = uid,
                onSuccess = {
                    sesionesAnaliticas = it
                    cargandoAnalitica = false
                },
                onError = {
                    cargandoAnalitica = false
                    error = "No se pudieron cargar todos tus hábitos."
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
                    error = "No se pudieron cargar todos tus hábitos."
                }
            )
        }
    }

    val cargando = cargandoAnalitica || cargandoDigital
    val inicioSemana = remember { inicioDeSemanaMillis() }

    // Para tiempo/sesiones/cambios de app usamos el historial propio del usuario.
    val digitalesSemana = sesionesDigitales.filter {
        it.inicioMillis >= inicioSemana
    }

    val tiempoSemana = digitalesSemana.sumOf { it.duracionS }
    val interrupcionesSemana = digitalesSemana.sumOf {
        it.interrupcionesDigitales
    }

    val promedioInterrupciones = if (digitalesSemana.isEmpty()) {
        0.0
    } else {
        interrupcionesSemana.toDouble() / digitalesSemana.size
    }

    val duracionPromedio = if (digitalesSemana.isEmpty()) {
        0
    } else {
        tiempoSemana / digitalesSemana.size
    }

    // Las posibles distracciones fisiológicas vienen de la analítica de la Raspberry.
    val analiticasSemana = sesionesAnaliticas.filter {
        it.inicioMillis >= inicioSemana
    }

    val distraccionesSemana = analiticasSemana.sumOf {
        it.nDistracciones
    }

    val momentoPreferido = calcularMomentoPreferidoDigital(
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
                "Tus hábitos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Text(
                "Patrones simples a partir de tus sesiones.",
                color = Color(0xFF686878)
            )

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    cargando -> {
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
                    }

                    error != null &&
                            sesionesDigitales.isEmpty() &&
                            sesionesAnaliticas.isEmpty() -> {
                        Text(
                            error.orEmpty(),
                            color = Color(0xFF686878)
                        )
                    }

                    sesionesDigitales.isEmpty() &&
                            sesionesAnaliticas.isEmpty() -> {
                        Text(
                            "Completa algunas sesiones para empezar a ver tus hábitos.",
                            color = Color(0xFF686878)
                        )
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                "Sesiones esta semana",
                                digitalesSemana.size.toString(),
                                Modifier.weight(1f)
                            )

                            MetricCard(
                                "Tiempo estudiado",
                                formatearTiempoLargo(tiempoSemana),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                "Cambios de app",
                                interrupcionesSemana.toString(),
                                Modifier.weight(1f)
                            )

                            MetricCard(
                                "Promedio cambios/sesión",
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    promedioInterrupciones
                                ),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard(
                                "Posibles distracciones",
                                distraccionesSemana.toString(),
                                Modifier.weight(1f)
                            )

                            MetricCard(
                                "Duración promedio",
                                formatearDuracion(duracionPromedio),
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        HabitCard(
                            title = "Uso del celular",
                            text = when {
                                digitalesSemana.isEmpty() ->
                                    "Aún no tienes sesiones esta semana."

                                interrupcionesSemana == 0 ->
                                    "No registraste cambios hacia otras aplicaciones esta semana."

                                promedioInterrupciones < 2.0 ->
                                    "Cambias de aplicación pocas veces durante tus sesiones."

                                else ->
                                    "Estás cambiando de aplicación varias veces durante tus sesiones de estudio."
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        HabitCard(
                            title = "Momento en que más estudias",
                            text = momentoPreferido
                        )
                    }
                }
            }

            ReFocusBottomBar(
                selected = "habits",
                onHome = onHomeClick,
                onStats = onStatsClick,
                onHabits = {},
                onTips = onTipsClick
            )
        }
    }
}

@Composable
private fun HabitCard(
    title: String,
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
                title,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text,
                color = Color(0xFF686878)
            )
        }
    }
}

private fun inicioDeSemanaMillis(): Long {
    val cal = Calendar.getInstance()

    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    return cal.timeInMillis
}

private fun calcularMomentoPreferidoDigital(
    sesiones: List<DigitalSession>
): String {
    if (sesiones.isEmpty()) {
        return "Aún no hay suficientes datos."
    }

    var manana = 0
    var tarde = 0
    var noche = 0

    sesiones.take(10).forEach { sesion ->
        val cal = Calendar.getInstance().apply {
            time = Date(sesion.inicioMillis)
        }

        when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> manana++
            in 12..17 -> tarde++
            else -> noche++
        }
    }

    val mayor = maxOf(manana, tarde, noche)

    return when (mayor) {
        manana -> "La mayoría de tus sesiones recientes han sido en la mañana."
        tarde -> "La mayoría de tus sesiones recientes han sido en la tarde."
        else -> "La mayoría de tus sesiones recientes han sido en la noche."
    }
}

private fun formatearTiempoLargo(segundos: Int): String {
    if (segundos < 60) {
        return "${segundos}s"
    }

    val horas = segundos / 3600
    val minutos = (segundos % 3600) / 60

    return when {
        horas == 0 -> "${minutos} min"
        minutos == 0 -> "${horas} h"
        else -> "${horas} h ${minutos} min"
    }
}
