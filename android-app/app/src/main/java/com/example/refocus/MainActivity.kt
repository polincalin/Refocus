package com.example.refocus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.refocus.ui.theme.ReFocusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            ReFocusTheme {

                var pantallaActual by remember {
                    mutableStateOf("login")
                }

                when (pantallaActual) {

                    "login" -> {

                        LoginScreen(
                            onRegisterClick = {
                                pantallaActual = "registro"
                            },

                            onLoginSuccess = {
                                pantallaActual = "home"
                            }
                        )
                    }

                    "registro" -> {

                        RegisterScreen(
                            onLoginClick = {
                                pantallaActual = "login"
                            }
                        )
                    }

                    "home" -> {

                        HomeScreen(
                            onLogout = {
                                pantallaActual = "login"
                            }
                        )
                    }
                }
            }
        }
    }
}