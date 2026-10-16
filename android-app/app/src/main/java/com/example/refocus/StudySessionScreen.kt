package com.example.refocus

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

@Composable
fun StudySessionScreen(
    onFinishSession: (Int) -> Unit
) {

    val context = LocalContext.current

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val uid = auth.currentUser?.uid

    // ID único de esta sesión
    val sessionId = remember {
        UUID.randomUUID().toString()
    }

    // La pantalla se abre cuando la Raspberry
    // confirma "Sesión en curso".
    val startTs = remember {
        System.currentTimeMillis()
    }

    var segundosTotales by remember {
        mutableIntStateOf(0)
    }

    var conectadoMQTT by remember {
        mutableStateOf(
            MqttManager.estaConectado()
        )
    }

    var estadoWearable by remember {
        mutableStateOf(
            "Sesión en curso"
        )
    }

    var finalizando by remember {
        mutableStateOf(false)
    }

    var guardandoFirestore by remember {
        mutableStateOf(false)
    }

    // --------------------------------
    // CREAR SESIÓN EN FIRESTORE
    // --------------------------------

    LaunchedEffect(
        uid,
        sessionId
    ) {

        if (uid == null) {

            Toast.makeText(
                context,
                "No se encontró el usuario",
                Toast.LENGTH_LONG
            ).show()

        } else {

            SessionRepository.crearSesion(
                uid = uid,
                sessionId = sessionId,
                startTs = startTs,

                onError = {

                    Toast.makeText(
                        context,
                        "No se pudo registrar el inicio de la sesión",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // --------------------------------
    // MQTT
    // --------------------------------

    DisposableEffect(Unit) {

        MqttManager.onConexionCambiada = { conectado ->

            conectadoMQTT = conectado

            if (!conectado) {

                estadoWearable =
                    "Sin conexión con el servidor"

                finalizando = false
            }
        }

        MqttManager.onEstadoRecibido = { nuevoEstado ->

            estadoWearable = nuevoEstado

            val raspberryConfirmoFin =
                finalizando &&
                        nuevoEstado.isNotBlank() &&
                        nuevoEstado != "Sesión en curso" &&
                        nuevoEstado != "Wearable desconectado" &&
                        !nuevoEstado.startsWith(
                            "Sin conexión",
                            ignoreCase = true
                        )

            if (raspberryConfirmoFin) {

                finalizando = false
                guardandoFirestore = true

                val endTs =
                    System.currentTimeMillis()

                val duracionReal =
                    (
                            (endTs - startTs) / 1000
                            ).toInt()

                // Si por alguna razón no hay UID,
                // terminamos igualmente la sesión local.
                if (uid == null) {

                    guardandoFirestore = false

                    onFinishSession(
                        duracionReal
                    )

                } else {

                    // Si sí tenemos usuario,
                    // guardamos el final en Firestore.
                    SessionRepository.finalizarSesion(
                        uid = uid,
                        sessionId = sessionId,
                        endTs = endTs,
                        duracionSegundos = duracionReal,

                        onSuccess = {

                            guardandoFirestore = false

                            onFinishSession(
                                duracionReal
                            )
                        },

                        onError = {

                            guardandoFirestore = false

                            Toast.makeText(
                                context,
                                "El wearable terminó, pero no se pudo guardar la sesión",
                                Toast.LENGTH_LONG
                            ).show()

                            // No dejamos atrapado al usuario.
                            onFinishSession(
                                duracionReal
                            )
                        }
                    )
                }
            }
        }

        onDispose {

            MqttManager.onConexionCambiada =
                null

            MqttManager.onEstadoRecibido =
                null
        }
    }

    // --------------------------------
    // CRONÓMETRO
    // --------------------------------

    LaunchedEffect(Unit) {

        while (true) {

            delay(1000)

            segundosTotales =
                (
                        (
                                System.currentTimeMillis()
                                        - startTs
                                ) / 1000
                        ).toInt()
        }
    }

    // --------------------------------
    // TIMEOUT AL FINALIZAR
    // --------------------------------

    LaunchedEffect(finalizando) {

        if (finalizando) {

            delay(15000)

            if (finalizando) {

                finalizando = false

                Toast.makeText(
                    context,
                    "No se recibió confirmación del wearable. Intenta nuevamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val minutos =
        segundosTotales / 60

    val segundos =
        segundosTotales % 60

    val tiempo =
        String.format(
            Locale.getDefault(),
            "%02d:%02d",
            minutos,
            segundos
        )

    // --------------------------------
    // INTERFAZ
    // --------------------------------

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 32.dp
                ),

            verticalArrangement =
                Arrangement.Center,

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "Sesión de estudio",

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
                    Modifier.height(14.dp)
            )

            Text(
                text =
                    when {

                        guardandoFirestore -> {
                            "Guardando sesión..."
                        }

                        finalizando -> {
                            "Finalizando sesión..."
                        }

                        else -> {
                            estadoWearable
                        }
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
                    Modifier.height(40.dp)
            )

            Text(
                text = tiempo,

                fontSize = 52.sp,

                fontWeight =
                    FontWeight.SemiBold,

                color =
                    Color(0xFF6755CE)
            )

            Spacer(
                modifier =
                    Modifier.height(42.dp)
            )

            Button(
                onClick = {

                    finalizando = true

                    MqttManager.enviarComando(
                        "finalizar"
                    ) { enviado ->

                        if (!enviado) {

                            finalizando = false

                            Toast.makeText(
                                context,
                                "No se pudo enviar el comando al wearable",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },

                enabled =
                    conectadoMQTT &&
                            !finalizando &&
                            !guardandoFirestore,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

                shape =
                    RoundedCornerShape(14.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF1D427A)
                    )
            ) {

                Text(
                    text =
                        when {

                            guardandoFirestore -> {
                                "Guardando..."
                            }

                            finalizando -> {
                                "Esperando confirmación..."
                            }

                            else -> {
                                "Finalizar sesión de estudio"
                            }
                        },

                    fontWeight =
                        FontWeight.Medium
                )
            }
        }
    }
}