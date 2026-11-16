package com.example.refocus

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
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
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val sessionId = remember { UUID.randomUUID().toString() }
    val startTs = remember { System.currentTimeMillis() }

    var segundosTotales by remember { mutableIntStateOf(0) }
    var posiblesDistracciones by remember { mutableIntStateOf(0) }
    var interrupcionesDigitales by remember { mutableIntStateOf(0) }
    var conectadoMQTT by remember { mutableStateOf(MqttManager.estaConectado()) }
    var estadoWearable by remember { mutableStateOf("Sesión en curso") }
    var finalizando by remember { mutableStateOf(false) }
    var guardandoFirestore by remember { mutableStateOf(false) }
    var estabaFueraDeRefocus by remember { mutableStateOf(false) }

    LaunchedEffect(uid, sessionId) {
        if (uid != null) {
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

    DisposableEffect(Unit) {
        MqttManager.onConexionCambiada = { conectado ->
            conectadoMQTT = conectado
            if (!conectado) {
                estadoWearable = "Sin conexión con el servidor"
                finalizando = false
            }
        }

        MqttManager.onEstadoRecibido = { nuevoEstado ->
            estadoWearable = nuevoEstado

            val raspberryConfirmoFin =
                finalizando &&
                        nuevoEstado == "Sesión finalizada"
            
            if (raspberryConfirmoFin) {
                finalizando = false
                guardandoFirestore = true

                val endTs = System.currentTimeMillis()
                val duracionReal = ((endTs - startTs) / 1000).toInt()

                if (uid == null) {
                    guardandoFirestore = false
                    onFinishSession(duracionReal)
                } else {
                    SessionRepository.finalizarSesion(
                        uid = uid,
                        sessionId = sessionId,
                        endTs = endTs,
                        duracionSegundos = duracionReal,
                        onSuccess = {
                            guardandoFirestore = false
                            onFinishSession(duracionReal)
                        },
                        onError = {
                            guardandoFirestore = false
                            Toast.makeText(
                                context,
                                "El wearable terminó, pero no se pudo guardar la sesión",
                                Toast.LENGTH_LONG
                            ).show()
                            onFinishSession(duracionReal)
                        }
                    )
                }
            }
        }

        MqttManager.onAlertaRecibida = { uidAlerta ->
            if (uidAlerta == null || uidAlerta == uid) {
                posiblesDistracciones++
                Toast.makeText(
                    context,
                    "Posible distracción detectada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        onDispose {
            MqttManager.onConexionCambiada = null
            MqttManager.onEstadoRecibido = null
            MqttManager.onAlertaRecibida = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            segundosTotales = ((System.currentTimeMillis() - startTs) / 1000).toInt()
        }
    }

    // Detecta transiciones hacia otra aplicación cuando el usuario otorgó
    // el permiso especial de acceso a estadísticas de uso.
    LaunchedEffect(uid, sessionId) {
        while (true) {
            delay(2000)

            if (uid != null && UsageAccessHelper.tienePermiso(context)) {
                val paquete = UsageAccessHelper.paqueteEnPrimerPlano(context)
                val fueraDeRefocus = paquete != null && paquete != context.packageName

                if (fueraDeRefocus && !estabaFueraDeRefocus) {
                    estabaFueraDeRefocus = true
                    interrupcionesDigitales++

                    // Guarda la interrupción digital en la sesión del usuario.
                    SessionRepository.registrarInterrupcionDigital(uid, sessionId)

                    // Activa remotamente el vibrador del wearable.
                    MqttManager.enviarComando(
                        comando = "vibrar_app",
                        uid = uid
                    )
                } else if (!fueraDeRefocus) {
                    estabaFueraDeRefocus = false
                }
            }
        }
    }

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

    val tiempo = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        segundosTotales / 60,
        segundosTotales % 60
    )

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F5FC)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                "Sesión de estudio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                when {
                    guardandoFirestore -> "Guardando sesión..."
                    finalizando -> "Finalizando sesión..."
                    else -> estadoWearable
                },
                color = Color(0xFF686878),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))

            Text(
                tiempo,
                fontSize = 52.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6755CE)
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Durante esta sesión",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1D427A)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Posibles distracciones detectadas: $posiblesDistracciones",
                        color = Color(0xFF686878)
                    )
                    Text(
                        "Cambios de app registrados: $interrupcionesDigitales",
                        color = Color(0xFF686878)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    finalizando = true
                    MqttManager.enviarComando("finalizar", uid = uid) { enviado ->
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
                enabled = conectadoMQTT && !finalizando && !guardandoFirestore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D427A))
            ) {
                Text(
                    when {
                        guardandoFirestore -> "Guardando..."
                        finalizando -> "Esperando confirmación..."
                        else -> "Finalizar sesión de estudio"
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

