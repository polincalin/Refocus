package com.example.refocus

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SessionSummaryScreen(
    duracionSegundos: Int,
    onBackHome: () -> Unit
) {

    val minutos = duracionSegundos / 60
    val segundos = duracionSegundos % 60

    val tiempo = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        minutos,
        segundos
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),

            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Sesión finalizada 🎉",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "Buen trabajo. Aquí tienes el resumen de tu sesión.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF686878),
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(36.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),

                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),

                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Duración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1D427A)
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    Text(
                        text = tiempo,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6755CE)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(36.dp)
            )

            Button(
                onClick = onBackHome,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

                shape = RoundedCornerShape(14.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6755CE)
                )
            ) {

                Text(
                    text = "Volver al inicio",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}