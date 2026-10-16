package com.example.refocus

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

@Composable
fun HomeScreen(
    onStartStudySession: () -> Unit,
    onStatsClick: () -> Unit,
    onLogout: () -> Unit
) {

    val context = LocalContext.current

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val usuario = auth.currentUser
    val uid = usuario?.uid

    // --------------------------------
    // NOMBRE DEL USUARIO
    // --------------------------------

    val nombreCompleto =
        usuario?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: usuario?.email?.substringBefore("@")
            ?: "Usuario"

    val nombre =
        nombreCompleto
            .trim()
            .substringBefore(" ")

    // --------------------------------
    // DATOS DE FIRESTORE
    // --------------------------------

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

    // Escuchamos las sesiones reales
    // guardadas para este usuario.
    DisposableEffect(uid) {

        if (uid == null) {

            onDispose { }

        } else {

            val listener =
                SessionRepository.escucharHome(

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

    // --------------------------------
    // ESTADO MQTT
    // --------------------------------

    var conectadoMQTT by remember {

        mutableStateOf(
            MqttManager.estaConectado()
        )
    }

    var estadoWearable by remember {

        mutableStateOf(
            if (conectadoMQTT) {

                "Conectado, esperando al wearable..."

            } else {

                "Conectando..."
            }
        )
    }

    var esperandoInicio by remember {
        mutableStateOf(false)
    }

    // --------------------------------
    // CONEXIÓN MQTT
    // --------------------------------

    DisposableEffect(Unit) {

        MqttManager.onConexionCambiada = { conectado ->

            conectadoMQTT = conectado

            if (conectado) {

                if (
                    estadoWearable == "Conectando..." ||
                    estadoWearable.startsWith(
                        "Sin conexión"
                    )
                ) {

                    estadoWearable =
                        "Conectado, esperando al wearable..."
                }

            } else {

                estadoWearable =
                    "Sin conexión con el servidor"

                esperandoInicio = false
            }
        }

        MqttManager.onEstadoRecibido = { nuevoEstado ->

            estadoWearable =
                nuevoEstado

            // Solo entramos a la sesión cuando
            // la Raspberry confirma el inicio.
            if (
                esperandoInicio &&
                nuevoEstado == "Sesión en curso"
            ) {

                esperandoInicio =
                    false

                onStartStudySession()
            }
        }

        MqttManager.conectar()

        onDispose {

            MqttManager.onConexionCambiada =
                null

            MqttManager.onEstadoRecibido =
                null
        }
    }

    // --------------------------------
    // CONDICIONES DEL WEARABLE
    // --------------------------------

    val desconectado =
        !conectadoMQTT ||
                estadoWearable ==
                "Wearable desconectado"

    val ocupado =
        estadoWearable.startsWith(
            "Calibrando"
        ) ||
                estadoWearable.startsWith(
                    "Preparando"
                )

    val enSesion =
        estadoWearable ==
                "Sesión en curso"

    // --------------------------------
    // FORMATO TIEMPO SEMANAL
    // --------------------------------

    val tiempoSemana = when {

        tiempoTotalSegundos < 60 -> {

            "$tiempoTotalSegundos s"
        }

        tiempoTotalSegundos < 3600 -> {

            "${tiempoTotalSegundos / 60} min"
        }

        else -> {

            val horas =
                tiempoTotalSegundos / 3600

            val minutos =
                (tiempoTotalSegundos % 3600) / 60

            if (minutos == 0) {

                "$horas h"

            } else {

                "$horas h $minutos min"
            }
        }
    }

    // --------------------------------
    // FORMATO ÚLTIMA SESIÓN
    // --------------------------------

    val minutosUltima =
        duracionUltimaSesion / 60

    val segundosUltima =
        duracionUltimaSesion % 60

    val ultimaSesionTexto =
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutosUltima,
            segundosUltima
        )

    val textoSesiones =
        if (cantidadSesiones == 1) {

            "sesión"

        } else {

            "sesiones"
        }

    // --------------------------------
    // INTERFAZ
    // --------------------------------

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(
                    top = 28.dp,
                    bottom = 18.dp
                )
        ) {

            // --------------------------------
            // ENCABEZADO
            // --------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

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
                        text =
                            "Cerrar sesión",

                        color =
                            Color(0xFF6755CE),

                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            // --------------------------------
            // SALUDO
            // --------------------------------

            Text(
                text =
                    "Hola, $nombre 👋",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF1D427A)
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    "¿Lista para concentrarte?",

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,

                color =
                    Color(0xFF686878)
            )

            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )

            // --------------------------------
            // ESTADO DEL WEARABLE
            // --------------------------------

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(18.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color.White
                    )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {

                    Text(
                        text =
                            "Estado del wearable",

                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF1D427A)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            estadoWearable,

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,

                        color =
                            Color(0xFF686878)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    Button(
                        onClick = {

                            MqttManager.enviarComando(
                                "calibrar"
                            ) { enviado ->

                                if (!enviado) {

                                    Toast.makeText(
                                        context,
                                        "No se pudo enviar el comando",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },

                        enabled =
                            !desconectado &&
                                    !ocupado &&
                                    !enSesion &&
                                    !esperandoInicio,

                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    Color(0xFF1D427A)
                            ),

                        shape =
                            RoundedCornerShape(
                                12.dp
                            )
                    ) {

                        Text(
                            text =
                                "Calibrar wearable"
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // --------------------------------
            // SESIÓN DE ESTUDIO
            // --------------------------------

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            "Sesión de estudio",

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
                            Modifier.height(12.dp)
                    )

                    Text(
                        text = "00:00",

                        fontSize = 34.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF6755CE)
                    )

                    Spacer(
                        modifier =
                            Modifier.height(18.dp)
                    )

                    Button(
                        onClick = {

                            esperandoInicio =
                                true

                            MqttManager.enviarComando(
                                "iniciar"
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
                            !desconectado &&
                                    !ocupado &&
                                    !enSesion &&
                                    !esperandoInicio,

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    Color(0xFF6755CE)
                            ),

                        shape =
                            RoundedCornerShape(
                                14.dp
                            )
                    ) {

                        Text(
                            text =
                                if (
                                    esperandoInicio
                                ) {

                                    "Esperando al wearable..."

                                } else {

                                    "Iniciar sesión de estudio"
                                },

                            fontWeight =
                                FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )

            // --------------------------------
            // TU SEMANA
            // --------------------------------

            Text(
                text =
                    "Tu semana",

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
                    Modifier.height(10.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        text =
                            tiempoSemana,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF6755CE)
                    )

                    Text(
                        text =
                            "de estudio",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        color =
                            Color(0xFF686878)
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text =
                            cantidadSesiones
                                .toString(),

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color(0xFF6755CE)
                    )

                    Text(
                        text =
                            textoSesiones,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        color =
                            Color(0xFF686878)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            // --------------------------------
            // ÚLTIMA SESIÓN
            // --------------------------------

            Text(
                text =
                    "Última sesión",

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
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    if (!hayUltimaSesion) {

                        "Aún no tienes sesiones registradas"

                    } else {

                        "Duración: $ultimaSesionTexto"
                    },

                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,

                color =
                    Color(0xFF686878)
            )

            Spacer(
                modifier =
                    Modifier.weight(1f)
            )

            // --------------------------------
            // NAVEGACIÓN INFERIOR
            // --------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                BottomItem(
                    texto =
                        "Inicio",

                    seleccionado =
                        true
                )

                BottomItem(
                    texto =
                        "Stats",

                    onClick =
                        onStatsClick
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


// --------------------------------
// BOTÓN DE NAVEGACIÓN INFERIOR
// --------------------------------

@Composable
fun BottomItem(
    texto: String,
    seleccionado: Boolean = false,
    onClick: () -> Unit = {}
) {

    TextButton(
        onClick = onClick
    ) {

        Text(
            text = texto,

            style =
                MaterialTheme
                    .typography
                    .labelMedium,

            fontWeight =
                if (seleccionado) {

                    FontWeight.SemiBold

                } else {

                    FontWeight.Normal
                },

            color =
                if (seleccionado) {

                    Color(0xFF6755CE)

                } else {

                    Color(0xFF8A8A99)
                },

            textAlign =
                TextAlign.Center
        )
    }
}