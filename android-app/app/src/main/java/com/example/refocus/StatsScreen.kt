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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    onHomeClick: () -> Unit,
    onHabitsClick: () -> Unit,
    onTipsClick: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sesiones by remember { mutableStateOf<List<AnalyticsSession>>(emptyList()) }

    LaunchedEffect(uid) {
        if (uid == null) {
            cargando = false
            error = "No se encontró el usuario."
        } else {
            AnalyticsRepository.cargarSesionesUsuario(
                uid = uid,
                onSuccess = {
                    sesiones = it
                    cargando = false
                },
                onError = {
                    error = "No se pudieron cargar las estadísticas."
                    cargando = false
                }
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F5FC)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 12.dp)
        ) {
            Text(
                "Tus estadísticas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )
            Text(
                "Un resumen sencillo de tus sesiones.",
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
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6755CE))
                        }
                    }

                    error != null -> {
                        Text(error.orEmpty(), color = Color(0xFF686878))
                    }

                    sesiones.isEmpty() -> {
                        Text("Aún no tienes sesiones analizadas.", color = Color(0xFF686878))
                    }

                    else -> {
                        val ultima = sesiones.first()
                        Text(
                            "Última sesión",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D427A)
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard("Duración", formatearDuracion(ultima.duracionS), Modifier.weight(1f))
                            MetricCard("Posibles distracciones", ultima.nDistracciones.toString(), Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricCard("Alertas", ultima.nAlertas.toString(), Modifier.weight(1f))
                            MetricCard(
                                "Tasa de enfoque",
                                ultima.tasaEnfoque?.let { "${it.toInt()} %" } ?: "-",
                                Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(22.dp))
                        Text(
                            "Sesiones recientes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D427A)
                        )
                        Spacer(Modifier.height(8.dp))

                        sesiones.take(6).forEach { sesion ->
                            SessionRow(sesion)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            ReFocusBottomBar(
                selected = "stats",
                onHome = onHomeClick,
                onStats = {},
                onHabits = onHabitsClick,
                onTips = onTipsClick
            )
        }
    }
}

@Composable
private fun SessionRow(sesion: AnalyticsSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sesion.inicioMillis)),
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1D427A)
                )
                Text(formatearDuracion(sesion.duracionS), color = Color(0xFF686878))
            }
            Text(
                "${sesion.nDistracciones} distr.",
                color = Color(0xFF6755CE),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatearDuracion(segundos: Int): String {
    val min = segundos / 60
    val seg = segundos % 60
    return if (min == 0) "${seg}s" else "${min}m ${seg}s"
}
