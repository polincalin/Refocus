package com.example.refocus

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var sesionesDigitales by remember {
        mutableStateOf<List<DigitalSession>>(emptyList())
    }

    var sesionesAnaliticas by remember {
        mutableStateOf<List<AnalyticsSession>>(emptyList())
    }

    var telemetria by remember {
        mutableStateOf<ThingsBoardStats?>(null)
    }

    var cargandoGraficas by remember {
        mutableStateOf(false)
    }

    var errorGraficas by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(uid) {

        if (uid == null) {
            cargando = false
            error = "No se encontró el usuario."
            return@LaunchedEffect
        }

        AnalyticsRepository.cargarSesionesDigitalesUsuario(
            uid = uid,

            onSuccess = { lista ->

                sesionesDigitales = lista
                cargando = false

                val ultima = lista.firstOrNull()

                if (ultima != null) {

                    cargandoGraficas = true
                    errorGraficas = null

                    val margen = 2000L

                    val startTs =
                        (ultima.inicioMillis - margen)
                            .coerceAtLeast(0L)

                    val endTs =
                        ultima.inicioMillis +
                                ultima.duracionS.coerceAtLeast(1) * 1000L +
                                margen

                    scope.launch {

                        ThingsBoardRepository.cargarIntervalo(
                            startTs = startTs,
                            endTs = endTs
                        )
                            .onSuccess {
                                telemetria = it
                                errorGraficas = null
                            }
                            .onFailure {
                                telemetria = null
                                errorGraficas =
                                    "No se pudieron cargar las gráficas."
                            }

                        cargandoGraficas = false
                    }
                }
            },

            onError = {
                cargando = false
                error = "No se pudieron cargar las sesiones."
            }
        )

        AnalyticsRepository.cargarSesionesUsuario(
            uid = uid,

            onSuccess = { lista ->
                sesionesAnaliticas = lista
            },

            onError = {
                // La pantalla sigue funcionando sin analítica técnica.
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 12.dp)
        ) {

            Text(
                text = "Tus estadísticas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Text(
                text = "Un resumen sencillo de tus sesiones.",
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

                    error != null -> {
                        Text(
                            text = error.orEmpty(),
                            color = Color(0xFF686878)
                        )
                    }

                    sesionesDigitales.isEmpty() -> {
                        Text(
                            text = "Aún no tienes sesiones registradas.",
                            color = Color(0xFF686878)
                        )
                    }

                    else -> {

                        val ultimaDigital = sesionesDigitales.first()

                        val ultimaAnalitica =
                            sesionesAnaliticas.firstOrNull()

                        Text(
                            text = "Última sesión",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D427A)
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            MetricCard(
                                title = "Duración",
                                value = formatearDuracion(
                                    ultimaDigital.duracionS
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Interrupciones digitales",
                                value = ultimaDigital
                                    .interrupcionesDigitales
                                    .toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // ANALÍTICA: LAS TARJETAS SIEMPRE APARECEN

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {

                            MetricCard(
                                title = "Alertas",
                                value = ultimaAnalitica
                                    ?.nAlertas
                                    ?.toString()
                                    ?: "—",
                                modifier = Modifier.weight(1f)
                            )

                            MetricCard(
                                title = "Posibles distracciones",
                                value = ultimaAnalitica
                                    ?.nDistracciones
                                    ?.toString()
                                    ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        MetricCard(
                            title = "Tasa de enfoque",
                            value = ultimaAnalitica
                                ?.tasaEnfoque
                                ?.let {
                                    "${
                                        formatearValor(
                                            it.toFloat(),
                                            0
                                        )
                                    }%"
                                }
                                ?: "—",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Señales de la última sesión",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D427A)
                        )

                        Text(
                            text = "Datos obtenidos desde ThingsBoard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF686878)
                        )

                        Spacer(Modifier.height(12.dp))

                        when {

                            cargandoGraficas -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(30.dp),
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF6755CE)
                                    )
                                }
                            }

                            errorGraficas != null -> {
                                Text(
                                    text = errorGraficas.orEmpty(),
                                    color = Color(0xFF686878)
                                )
                            }

                            telemetria != null -> {

                                TelemetryChart(
                                    title = "Ritmo cardiaco",
                                    unit = "BPM",
                                    points = telemetria!!.bpm,
                                    color = Color(0xFF6755CE),
                                    decimals = 0
                                )

                                Spacer(Modifier.height(12.dp))

                                TelemetryChart(
                                    title = "GSR",
                                    unit = "V",
                                    points = telemetria!!.gsr,
                                    color = Color(0xFF1D427A),
                                    decimals = 3
                                )

                                Spacer(Modifier.height(12.dp))

                                TelemetryChart(
                                    title = "Movimiento",
                                    unit = "g",
                                    points = telemetria!!.movimiento,
                                    color = Color(0xFF7B68EE),
                                    decimals = 3
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "Sesiones recientes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D427A)
                        )

                        Spacer(Modifier.height(8.dp))

                        sesionesDigitales.take(6).forEach { sesion ->

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
private fun TelemetryChart(
    title: String,
    unit: String,
    points: List<TelemetryPoint>,
    color: Color,
    decimals: Int
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            if (points.isEmpty()) {

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Sin datos en esta sesión.",
                    color = Color(0xFF686878)
                )

                return@Column
            }

            val valores = points.map { it.value }
            val minimo = valores.minOrNull() ?: 0f
            val maximo = valores.maxOrNull() ?: 0f
            val promedio = valores.average().toFloat()

            Text(
                text = "Promedio: ${
                    formatearValor(promedio, decimals)
                } $unit",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF686878)
            )

            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {

                val margen = 8.dp.toPx()
                val ancho = size.width - margen * 2
                val alto = size.height - margen * 2

                repeat(4) { i ->

                    val y = margen + alto * i / 3f

                    drawLine(
                        color = Color(0xFFE8E5F0),
                        start = Offset(margen, y),
                        end = Offset(margen + ancho, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (points.size == 1) {

                    drawCircle(
                        color = color,
                        radius = 4.dp.toPx(),
                        center = Offset(
                            size.width / 2,
                            size.height / 2
                        )
                    )

                } else {

                    val rango =
                        (maximo - minimo)
                            .takeIf { it > 0.0001f }
                            ?: 1f

                    val path = Path()

                    points.forEachIndexed { index, punto ->

                        val x =
                            margen +
                                    ancho * index /
                                    (points.size - 1).toFloat()

                        val normalizado =
                            (punto.value - minimo) / rango

                        val y =
                            margen +
                                    alto -
                                    normalizado * alto

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "Mín. ${
                        formatearValor(minimo, decimals)
                    } $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A99)
                )

                Text(
                    text = "Máx. ${
                        formatearValor(maximo, decimals)
                    } $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8A8A99)
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    sesion: DigitalSession
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.getDefault()
                    ).format(
                        Date(sesion.inicioMillis)
                    ),
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1D427A)
                )

                Text(
                    text = formatearDuracion(
                        sesion.duracionS
                    ),
                    color = Color(0xFF686878)
                )
            }

            Text(
                text = "${sesion.interrupcionesDigitales} interrup.",
                color = Color(0xFF6755CE),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatearDuracion(segundos: Int): String {

    val min = segundos / 60
    val seg = segundos % 60

    return if (min == 0) {
        "${seg}s"
    } else {
        "${min}m ${seg}s"
    }
}

private fun formatearValor(
    valor: Float,
    decimals: Int
): String {

    return String.format(
        Locale.getDefault(),
        "%.${decimals}f",
        valor
    )
}
