package com.example.refocus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.refocus.ui.theme.ReFocusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContent {

            ReFocusTheme {

                var pantallaActual by remember {
                    mutableStateOf("login")
                }

                var duracionUltimaSesion by remember {
                    mutableIntStateOf(0)
                }

                when (pantallaActual) {

                    // -------------------------
                    // LOGIN
                    // -------------------------

                    "login" -> {

                        LoginScreen(

                            onRegisterClick = {

                                pantallaActual =
                                    "registro"
                            },

                            onLoginSuccess = {

                                pantallaActual =
                                    "home"
                            }
                        )
                    }

                    // -------------------------
                    // REGISTRO
                    // -------------------------

                    "registro" -> {

                        RegisterScreen(

                            onLoginClick = {

                                pantallaActual =
                                    "login"
                            }
                        )
                    }

                    // -------------------------
                    // HOME
                    // -------------------------

                    "home" -> {

                        HomeScreen(

                            onStartStudySession = {

                                pantallaActual =
                                    "study"
                            },

                            onStatsClick = {

                                pantallaActual =
                                    "stats"
                            },

                            onLogout = {

                                pantallaActual =
                                    "login"
                            }
                        )
                    }

                    // -------------------------
                    // SESIÓN DE ESTUDIO
                    // -------------------------

                    "study" -> {

                        StudySessionScreen(

                            onFinishSession = {
                                    duracion ->

                                duracionUltimaSesion =
                                    duracion

                                pantallaActual =
                                    "summary"
                            }
                        )
                    }

                    // -------------------------
                    // RESUMEN
                    // -------------------------

                    "summary" -> {

                        SessionSummaryScreen(

                            duracionSegundos =
                                duracionUltimaSesion,

                            onBackHome = {

                                pantallaActual =
                                    "home"
                            }
                        )
                    }

                    // -------------------------
                    // STATS
                    // -------------------------

                    "stats" -> {

                        StatsScreen(

                            onHomeClick = {

                                pantallaActual =
                                    "home"
                            }
                        )
                    }
                }
            }
        }
    }
}