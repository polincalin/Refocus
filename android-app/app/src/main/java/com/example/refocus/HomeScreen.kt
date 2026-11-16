package com.example.refocus

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

@Composable
fun HomeScreen(
    onStartStudySession: () -> Unit,
    onStatsClick: () -> Unit,
    onHabitsClick: () -> Unit,
    onTipsClick: () -> Unit,
    onLogout: () -> Unit
) {

    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    val usuario = auth.currentUser
    val uid = usuario?.uid

    val nombreCompleto =
        usuario?.displayName?.takeIf { it.isNotBlank() }
            ?: usuario?.email?.substringBefore("@")
            ?: "Usuario"

    val nombre = nombreCompleto.trim().substringBefore(" ")

    // ---------------------------------------------------------
    // PERMISO DE USO DE APLICACIONES
    // ---------------------------------------------------------

    var permisoUso by remember {
        mutableStateOf(UsageAccessHelper.tienePermiso(context))
    }

    val launcherPermisoUso =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            // Se ejecuta cuando el usuario vuelve desde Ajustes
            permisoUso = UsageAccessHelper.tienePermiso(context)
        }

    // ---------------------------------------------------------
    // ESTADÍSTICAS DEL USUARIO
    // ---------------------------------------------------------

    var tiempoTotalSegundos by remember {
        mutableIntStateOf(0)
    }

    var cantidadSesiones by remember {
        mutableIntStateOf(0)
    }

    var duracionUltimaSesion by remember {
        mutableIntStateOf(0)
    }

    var hayUltimaSesion by remember {
        mutableStateOf(false)
    }

    DisposableEffect(uid) {

        if (uid == null) {

            onDispose { }

        } else {

            val listener = SessionRepository.escucharHome(

                uid = uid,

                onUpdate = { stats ->

                    tiempoTotalSegundos =
                        stats.tiempoTotalSegundos

                    cantidadSesiones =
                        stats.cantidadSesiones

                    duracionUltimaSesion =
                        stats.duracionUltimaSesion

                    hayUltimaSesion =
                        stats.hayUltimaSesion
                },

                onError = {

                    Toast.makeText(
                        context,
                        "No se pudieron cargar las sesiones",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            onDispose {
                listener.remove()
            }
        }
    }

    // ---------------------------------------------------------
    // MQTT
    // ---------------------------------------------------------

    var conectadoMQTT by remember {
        mutableStateOf(MqttManager.estaConectado())
    }

    var estadoWearable by remember {

        mutableStateOf(
            if (conectadoMQTT)
                "Conectado, esperando al wearable..."
            else
                "Conectando..."
        )
    }

    var esperandoInicio by remember {
        mutableStateOf(false)
    }

    DisposableEffect(Unit) {

        MqttManager.onConexionCambiada = { conectado ->

            conectadoMQTT = conectado

            if (!conectado) {

                estadoWearable =
                    "Sin conexión con el servidor"

                esperandoInicio = false

            } else if (
                estadoWearable == "Conectando..." ||
                estadoWearable.startsWith("Sin conexión")
            ) {

                estadoWearable =
                    "Conectado, esperando al wearable..."
            }
        }

        MqttManager.onEstadoRecibido = { nuevoEstado ->

            estadoWearable = nuevoEstado

            if (
                esperandoInicio &&
                nuevoEstado == "Sesión en curso"
            ) {

                esperandoInicio = false

                onStartStudySession()
            }
        }

        MqttManager.conectar()

        onDispose {

            MqttManager.onConexionCambiada = null

            MqttManager.onEstadoRecibido = null
        }
    }

    // ---------------------------------------------------------
    // ESTADO DEL WEARABLE
    // ---------------------------------------------------------

    val desconectado =
        !conectadoMQTT ||
                estadoWearable == "Wearable desconectado"

    val ocupado =
        estadoWearable.startsWith("Calibrando") ||
                estadoWearable.startsWith("Preparando")

    val enSesion =
        estadoWearable == "Sesión en curso"

    // ---------------------------------------------------------
    // TEXTO DE TIEMPO
    // ---------------------------------------------------------

    val tiempoSemana = when {

        tiempoTotalSegundos < 60 ->
            "$tiempoTotalSegundos s"

        tiempoTotalSegundos < 3600 ->
            "${tiempoTotalSegundos / 60} min"

        else -> {

            val h =
                tiempoTotalSegundos / 3600

            val m =
                (tiempoTotalSegundos % 3600) / 60

            if (m == 0)
                "$h h"
            else
                "$h h $m min"
        }
    }

    val ultimaSesionTexto =
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            duracionUltimaSesion / 60,
            duracionUltimaSesion % 60
        )

    // ---------------------------------------------------------
    // INTERFAZ
    // ---------------------------------------------------------

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(
                    top = 24.dp,
                    bottom = 12.dp
                )
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {

                // -------------------------------------------------
                // HEADER
                // -------------------------------------------------

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Image(
                        painter =
                            painterResource(
                                id = R.drawable.logo
                            ),
                        contentDescription =
                            "Logo ReFocus",
                        modifier =
                            Modifier.width(115.dp),
                        contentScale =
                            ContentScale.Fit
                    )

                    TextButton(
                        onClick = {

                            MqttManager.desconectar()

                            auth.signOut()

                            onLogout()
                        }
                    ) {

                        Text(
                            "Cerrar sesión",
                            color = Color(0xFF6755CE)
                        )
                    }
                }

                Spacer(
                    Modifier.height(24.dp)
                )

                Text(
                    text = "Hola, $nombre 👋",
                    style =
                        MaterialTheme.typography
                            .headlineSmall,
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        Color(0xFF1D427A)
                )

                Text(
                    text =
                        "Prepara tu próxima sesión de estudio.",
                    color =
                        Color(0xFF686878)
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                // -------------------------------------------------
                // WEARABLE
                // -------------------------------------------------

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
                        Modifier.padding(18.dp)
                    ) {

                        Text(
                            "Wearable",
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                Color(0xFF1D427A)
                        )

                        Text(
                            estadoWearable,
                            color =
                                Color(0xFF686878)
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Button(
                            onClick = {

                                MqttManager
                                    .enviarComando(
                                        "calibrar"
                                    )
                            },

                            enabled =
                                !desconectado &&
                                        !ocupado &&
                                        !enSesion &&
                                        !esperandoInicio,

                            modifier =
                                Modifier.fillMaxWidth(),

                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color(0xFF1D427A)
                                    )
                        ) {

                            Text(
                                "Calibrar wearable"
                            )
                        }
                    }
                }

                // -------------------------------------------------
                // PERMISO CAMBIO DE APLICACIONES
                // -------------------------------------------------

                if (!permisoUso) {

                    Spacer(
                        Modifier.height(14.dp)
                    )

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(18.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color(0xFFF0ECFF)
                            )
                    ) {

                        Column(
                            Modifier.padding(16.dp)
                        ) {

                            Text(
                                "Detección de cambios de app",
                                fontWeight =
                                    FontWeight.SemiBold,
                                color =
                                    Color(0xFF1D427A)
                            )

                            Text(
                                "Activa el acceso de uso para registrar cuando sales de ReFocus durante una sesión.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    Color(0xFF686878)
                            )

                            TextButton(
                                onClick = {

                                    val intent =
                                        Intent(
                                            Settings
                                                .ACTION_USAGE_ACCESS_SETTINGS
                                        )

                                    launcherPermisoUso
                                        .launch(intent)
                                }
                            ) {

                                Text(
                                    "Dar permiso",
                                    color =
                                        Color(0xFF6755CE)
                                )
                            }
                        }
                    }
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                // -------------------------------------------------
                // INICIAR SESIÓN
                // -------------------------------------------------

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(22.dp),
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
                                .padding(22.dp),

                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            "Sesión de estudio",
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                Color(0xFF1D427A)
                        )

                        Spacer(
                            Modifier.height(14.dp)
                        )

                        Button(

                            onClick = {

                                esperandoInicio = true

                                MqttManager
                                    .enviarComando(

                                        comando =
                                            "iniciar",

                                        uid = uid

                                    ) { enviado ->

                                        if (!enviado) {

                                            esperandoInicio =
                                                false

                                            Toast.makeText(
                                                context,
                                                "No se pudo iniciar el wearable",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            },

                            enabled =
                                uid != null &&
                                        !desconectado &&
                                        !ocupado &&
                                        !enSesion &&
                                        !esperandoInicio,

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),

                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            Color(0xFF6755CE)
                                    ),

                            shape =
                                RoundedCornerShape(14.dp)
                        ) {

                            Text(
                                if (esperandoInicio)
                                    "Esperando al wearable..."
                                else
                                    "Iniciar sesión de estudio"
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(20.dp)
                )

                // -------------------------------------------------
                // RESUMEN SEMANAL
                // -------------------------------------------------

                Text(
                    "Tu semana",
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        Color(0xFF1D427A)
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    MetricCard(
                        "Tiempo de estudio",
                        tiempoSemana,
                        Modifier.weight(1f)
                    )

                    MetricCard(
                        "Sesiones",
                        cantidadSesiones.toString(),
                        Modifier.weight(1f)
                    )
                }

                Spacer(
                    Modifier.height(18.dp)
                )

                Text(
                    "Última sesión",
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        Color(0xFF1D427A)
                )

                Text(
                    if (!hayUltimaSesion)
                        "Aún no tienes sesiones registradas"
                    else
                        "Duración: $ultimaSesionTexto",

                    color =
                        Color(0xFF686878)
                )

                Spacer(
                    Modifier.height(18.dp)
                )
            }

            // -----------------------------------------------------
            // BARRA INFERIOR
            // -----------------------------------------------------

            ReFocusBottomBar(
                selected = "home",
                onHome = {},
                onStats = onStatsClick,
                onHabits = onHabitsClick,
                onTips = onTipsClick
            )
        }
    }
}