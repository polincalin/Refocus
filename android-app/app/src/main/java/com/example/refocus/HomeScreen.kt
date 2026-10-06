package com.example.refocus

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.TextButton

@Composable
fun HomeScreen(
    onLogout: () -> Unit
) {

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val nombre = auth.currentUser?.displayName ?: "Usuario"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F5FC)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 18.dp)
        ) {

            // Encabezado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo ReFocus",
                    modifier = Modifier.width(115.dp),
                    contentScale = ContentScale.Fit
                )

                TextButton(
                    onClick = {

                        auth.signOut()

                        onLogout()
                    }
                ) {

                    Text(
                        text = "Cerrar sesión",
                        color = Color(0xFF6755CE),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Text(
                text = "Hola, $nombre 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "¿Lista para concentrarte?",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF686878)
            )

            Spacer(
                modifier = Modifier.height(34.dp)
            )

            // Sesión de estudio
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Sesión de estudio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1D427A)
                    )

                    Spacer(
                        modifier = Modifier.height(18.dp)
                    )

                    Text(
                        text = "00:00",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6755CE)
                    )

                    Spacer(
                        modifier = Modifier.height(22.dp)
                    )

                    Button(
                        onClick = {
                            // Después abriremos la pantalla de sesión activa
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6755CE)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {

                        Text(
                            text = "Iniciar sesión de estudio",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            // Datos semanales
            Text(
                text = "Tu semana",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        text = "0 min",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6755CE)
                    )

                    Text(
                        text = "de estudio",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF686878)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {

                    Text(
                        text = "0",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6755CE)
                    )

                    Text(
                        text = "sesiones",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF686878)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            Text(
                text = "Última sesión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D427A)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Aún no tienes sesiones registradas",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF686878)
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            // Navegación inferior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                BottomItem(
                    texto = "Inicio",
                    seleccionado = true
                )

                BottomItem(
                    texto = "Stats"
                )

                BottomItem(
                    texto = "Hábitos"
                )

                BottomItem(
                    texto = "Consejos"
                )
            }
        }
    }
}

@Composable
fun BottomItem(
    texto: String,
    seleccionado: Boolean = false
) {

    Box(
        modifier = Modifier.padding(6.dp),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (seleccionado) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            color = if (seleccionado) {
                Color(0xFF6755CE)
            } else {
                Color(0xFF8A8A99)
            },
            textAlign = TextAlign.Center
        )
    }
}