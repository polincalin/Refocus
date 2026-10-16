package com.example.refocus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    onHomeClick: () -> Unit
) {

    var cargando by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var datos by remember {
        mutableStateOf(
            ThingsBoardStats()
        )
    }

    // =========================================================
    // CARGAR DATOS DE THINGSBOARD
    // =========================================================

    LaunchedEffect(Unit) {

        cargando =
            true

        error =
            null

        ThingsBoardRepository
            .cargarUltimosSieteDias()
            .onSuccess {

                datos =
                    it

                cargando =
                    false
            }
            .onFailure {

                error =
                    it.message
                        ?: "No se pudieron cargar los datos"

                cargando =
                    false
            }
    }

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            Color(0xFFF7F5FC)
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize()
        ) {

            // =================================================
            // ENCABEZADO
            // =================================================

            Text(
                text =
                    "Tus estadísticas",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF1D427A),

                modifier =
                    Modifier.padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 28.dp,
                        bottom = 6.dp
                    )
            )

            Text(
                text =
                    "Señales registradas por ReFocus.",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,

                color =
                    Color(0xFF686878),

                modifier =
                    Modifier.padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 16.dp
                    )
            )

            // =================================================
            // CONTENIDO
            // =================================================

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
            ) {

                when {

                    // -----------------------------------------
                    // CARGANDO
                    // -----------------------------------------

                    cargando -> {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),

                            horizontalAlignment =
                                Alignment.CenterHorizontally,

                            verticalArrangement =
                                Arrangement.Center
                        ) {

                            CircularProgressIndicator(
                                color =
                                    Color(0xFF6755CE)
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(16.dp)
                            )

                            Text(
                                text =
                                    "Cargando datos de ThingsBoard...",

                                color =
                                    Color(0xFF686878)
                            )
                        }
                    }

                    // -----------------------------------------
                    // ERROR
                    // -----------------------------------------

                    error != null -> {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),

                            horizontalAlignment =
                                Alignment.CenterHorizontally,

                            verticalArrangement =
                                Arrangement.Center
                        ) {

                            Text(
                                text =
                                    "No pudimos cargar las estadísticas.",

                                fontWeight =
                                    FontWeight.SemiBold,

                                color =
                                    Color(0xFF1D427A)
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )

                            Text(
                                text =
                                    error.orEmpty(),

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,

                                color =
                                    Color(0xFF686878)
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(18.dp)
                            )

                            Button(
                                onClick = {

                                    cargando =
                                        true

                                    error =
                                        null
                                }
                            ) {

                                Text(
                                    "Volver a intentar"
                                )
                            }
                        }
                    }

                    // -----------------------------------------
                    // DATOS
                    // -----------------------------------------

                    else -> {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        rememberScrollState()
                                    )
                                    .padding(
                                        horizontal = 20.dp
                                    )
                        ) {

                            ChartCard(
                                titulo =
                                    "Frecuencia cardiaca",

                                subtitulo =
                                    "BPM",

                                puntos =
                                    datos.bpm,

                                colorLinea =
                                    Color(0xFF6755CE),

                                decimales =
                                    1
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(16.dp)
                            )

                            ChartCard(
                                titulo =
                                    "Respuesta galvánica",

                                subtitulo =
                                    "Voltaje GSR",

                                puntos =
                                    datos.gsr,

                                colorLinea =
                                    Color(0xFF1D427A),

                                decimales =
                                    3
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(16.dp)
                            )

                            ChartCard(
                                titulo =
                                    "Movimiento",

                                subtitulo =
                                    "Nivel de movimiento",

                                puntos =
                                    datos.movimiento,

                                colorLinea =
                                    Color(0xFF6755CE),

                                decimales =
                                    4
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(20.dp)
                            )
                        }
                    }
                }
            }

            // =================================================
            // NAVEGACIÓN INFERIOR
            // =================================================

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                BottomItem(
                    texto =
                        "Inicio",

                    onClick =
                        onHomeClick
                )

                BottomItem(
                    texto =
                        "Stats",

                    seleccionado =
                        true
                )

                BottomItem(
                    texto =
                        "Hábitos"
                )

                BottomItem(
                    texto =
                        "Consejos"
                )
            }
        }
    }
}

// =============================================================
// TARJETA DE GRÁFICA
// =============================================================

@Composable
private fun ChartCard(
    titulo: String,
    subtitulo: String,
    puntos: List<TelemetryPoint>,
    colorLinea: Color,
    decimales: Int
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
        ) {

            Text(
                text =
                    titulo,

                style =
                    MaterialTheme
                        .typography
                        .titleMedium,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF1D427A)
            )

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text =
                    subtitulo,

                style =
                    MaterialTheme
                        .typography
                        .bodySmall,

                color =
                    Color(0xFF686878)
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            if (
                puntos.isEmpty()
            ) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(170.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "Sin datos disponibles",

                        color =
                            Color(0xFF8A8A99)
                    )
                }

            } else {

                val ultimo =
                    puntos.last()

                val formato =
                    "%.${decimales}f"

                Text(
                    text =
                        String.format(
                            Locale.getDefault(),
                            formato,
                            ultimo.value
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.SemiBold,

                    color =
                        colorLinea
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                LineChart(
                    puntos =
                        puntos,

                    colorLinea =
                        colorLinea
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        text =
                            formatearFecha(
                                puntos.first().ts
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            Color(0xFF8A8A99)
                    )

                    Text(
                        text =
                            formatearFecha(
                                puntos.last().ts
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            Color(0xFF8A8A99)
                    )
                }
            }
        }
    }
}

// =============================================================
// GRÁFICA NATIVA
// =============================================================

@Composable
private fun LineChart(
    puntos: List<TelemetryPoint>,
    colorLinea: Color
) {

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp)
    ) {

        if (
            puntos.isEmpty()
        ) {

            return@Canvas
        }

        val margen =
            14f

        val ancho =
            size.width -
                    (2f * margen)

        val alto =
            size.height -
                    (2f * margen)

        // -----------------------------------------------------
        // LÍNEAS DE FONDO
        // -----------------------------------------------------

        val colorCuadricula =
            Color(0xFFE8E6EF)

        for (
        i in 0..3
        ) {

            val y =
                margen +
                        (
                                alto *
                                        i /
                                        3f
                                )

            drawLine(
                color =
                    colorCuadricula,

                start =
                    Offset(
                        margen,
                        y
                    ),

                end =
                    Offset(
                        size.width -
                                margen,
                        y
                    ),

                strokeWidth =
                    1.5f
            )
        }

        // -----------------------------------------------------
        // RANGO Y
        // -----------------------------------------------------

        val minimo =
            puntos.minOf {
                it.value
            }

        val maximo =
            puntos.maxOf {
                it.value
            }

        val rango =
            if (
                maximo - minimo <
                0.000001f
            ) {

                1f

            } else {

                maximo - minimo
            }

        // -----------------------------------------------------
        // RANGO X
        // -----------------------------------------------------

        val primerTs =
            puntos.first().ts

        val ultimoTs =
            puntos.last().ts

        val rangoTs =
            if (
                ultimoTs == primerTs
            ) {

                1L

            } else {

                ultimoTs -
                        primerTs
            }

        // -----------------------------------------------------
        // UN SOLO PUNTO
        // -----------------------------------------------------

        if (
            puntos.size == 1
        ) {

            drawCircle(
                color =
                    colorLinea,

                radius =
                    6f,

                center =
                    Offset(
                        size.width / 2f,
                        size.height / 2f
                    )
            )

            return@Canvas
        }

        // -----------------------------------------------------
        // CAMINO DE LA GRÁFICA
        // -----------------------------------------------------

        val path =
            Path()

        puntos.forEachIndexed {
                index,
                punto ->

            val porcentajeX =
                (
                        punto.ts -
                                primerTs
                        ).toFloat() /
                        rangoTs.toFloat()

            val porcentajeY =
                (
                        punto.value -
                                minimo
                        ) /
                        rango

            val x =
                margen +
                        (
                                porcentajeX *
                                        ancho
                                )

            val y =
                margen +
                        alto -
                        (
                                porcentajeY *
                                        alto
                                )

            if (
                index == 0
            ) {

                path.moveTo(
                    x,
                    y
                )

            } else {

                path.lineTo(
                    x,
                    y
                )
            }
        }

        drawPath(
            path =
                path,

            color =
                colorLinea,

            style =
                Stroke(
                    width = 4f,
                    cap = StrokeCap.Round
                )
        )
    }
}

// =============================================================
// FORMATO DE FECHA
// =============================================================

private fun formatearFecha(
    timestamp: Long
): String {

    val formatter =
        SimpleDateFormat(
            "dd/MM HH:mm",
            Locale.getDefault()
        )

    return formatter.format(
        Date(timestamp)
    )
}