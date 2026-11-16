package com.example.refocus

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(
    onRegisterClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var cargando by remember {
        mutableStateOf(false)
    }

    var recuperando by remember {
        mutableStateOf(false)
    }

    val context = LocalContext.current

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    val coloresCampo = OutlinedTextFieldDefaults.colors(

        focusedTextColor = Color(0xFF1D427A),
        unfocusedTextColor = Color(0xFF1D427A),

        focusedLabelColor = Color(0xFF6755CE),
        unfocusedLabelColor = Color(0xFF686878),

        focusedBorderColor = Color(0xFF6755CE),
        unfocusedBorderColor = Color(0xFF9A9AA3),

        cursorColor = Color(0xFF6755CE),

        disabledTextColor = Color(0xFF686878),
        disabledBorderColor = Color(0xFFB7B7C0),
        disabledLabelColor = Color(0xFF8A8A95)
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

            Image(
                painter = painterResource(
                    id = R.drawable.logo
                ),

                contentDescription = "Logo ReFocus",

                modifier = Modifier.width(190.dp),

                contentScale = ContentScale.Fit
            )

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            Text(
                text = "Inicia sesión",

                style = MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.SemiBold,

                color = Color(0xFF1D427A)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Continúa con tu cuenta de ReFocus",

                style = MaterialTheme.typography.bodyMedium,

                color = Color(0xFF686878),

                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            // CORREO
            OutlinedTextField(

                value = email,

                onValueChange = {
                    email = it
                },

                label = {
                    Text("Correo electrónico")
                },

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),

                singleLine = true,

                enabled = !cargando && !recuperando,

                modifier = Modifier.fillMaxWidth(),

                colors = coloresCampo
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            // CONTRASEÑA
            OutlinedTextField(

                value = password,

                onValueChange = {
                    password = it
                },

                label = {
                    Text("Contraseña")
                },

                visualTransformation =
                    PasswordVisualTransformation(),

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),

                singleLine = true,

                enabled = !cargando && !recuperando,

                modifier = Modifier.fillMaxWidth(),

                colors = coloresCampo
            )

            // OLVIDÉ MI CONTRASEÑA
            TextButton(

                onClick = {

                    val emailLimpio =
                        email.trim()

                    if (emailLimpio.isEmpty()) {

                        Toast.makeText(
                            context,
                            "Escribe primero tu correo electrónico",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        recuperando = true

                        auth.sendPasswordResetEmail(
                            emailLimpio
                        )
                            .addOnCompleteListener { tarea ->

                                recuperando = false

                                if (tarea.isSuccessful) {

                                    Toast.makeText(
                                        context,
                                        "Te enviamos un correo para restablecer tu contraseña",
                                        Toast.LENGTH_LONG
                                    ).show()

                                } else {

                                    Toast.makeText(
                                        context,
                                        "No se pudo enviar el correo de recuperación",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                },

                enabled =
                    !cargando &&
                            !recuperando,

                modifier =
                    Modifier.align(
                        Alignment.End
                    )
            ) {

                Text(
                    text =
                        if (recuperando)
                            "Enviando..."
                        else
                            "¿Olvidaste tu contraseña?",

                    color =
                        Color(0xFF6755CE)
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            // INICIAR SESIÓN
            Button(

                onClick = {

                    val emailLimpio =
                        email.trim()

                    if (
                        emailLimpio.isEmpty() ||
                        password.isEmpty()
                    ) {

                        Toast.makeText(
                            context,
                            "Completa todos los campos",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        cargando = true

                        auth.signInWithEmailAndPassword(
                            emailLimpio,
                            password
                        )
                            .addOnCompleteListener { tarea ->

                                cargando = false

                                if (tarea.isSuccessful) {

                                    onLoginSuccess()

                                } else {

                                    Toast.makeText(
                                        context,
                                        "No se pudo iniciar sesión. Revisa tus datos.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                },

                enabled =
                    !cargando &&
                            !recuperando,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(0xFF6755CE)
                    )
            ) {

                Text(
                    text =
                        if (cargando)
                            "Iniciando sesión..."
                        else
                            "Iniciar sesión",

                    fontWeight =
                        FontWeight.Medium,

                    fontSize = 15.sp
                )
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Text(
                text = "¿No tienes una cuenta?",

                style =
                    MaterialTheme.typography.bodySmall,

                color =
                    Color(0xFF686878),

                textAlign =
                    TextAlign.Center
            )

            TextButton(

                onClick =
                    onRegisterClick,

                enabled =
                    !cargando &&
                            !recuperando
            ) {

                Text(
                    text = "Regístrate",

                    color =
                        Color(0xFF6755CE),

                    fontWeight =
                        FontWeight.Medium
                )
            }
        }
    }
}