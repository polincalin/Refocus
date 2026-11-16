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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            ReFocusTheme(
                darkTheme = false,
                dynamicColor = false
            ) {

                var pantallaActual by remember {
                    mutableStateOf("login")
                }

                var duracionUltimaSesion by remember {
                    mutableIntStateOf(0)
                }

                when (pantallaActual) {

                    "login" -> LoginScreen(
                        onRegisterClick = {
                            pantallaActual = "registro"
                        },
                        onLoginSuccess = {
                            pantallaActual = "home"
                        }
                    )

                    "registro" -> RegisterScreen(
                        onLoginClick = {
                            pantallaActual = "login"
                        }
                    )

                    "home" -> HomeScreen(
                        onStartStudySession = {
                            pantallaActual = "study"
                        },
                        onStatsClick = {
                            pantallaActual = "stats"
                        },
                        onHabitsClick = {
                            pantallaActual = "habits"
                        },
                        onTipsClick = {
                            pantallaActual = "tips"
                        },
                        onLogout = {
                            pantallaActual = "login"
                        }
                    )

                    "study" -> StudySessionScreen(
                        onFinishSession = { duracion ->

                            duracionUltimaSesion = duracion

                            pantallaActual = "summary"
                        }
                    )

                    "summary" -> SessionSummaryScreen(
                        duracionSegundos = duracionUltimaSesion,
                        onBackHome = {
                            pantallaActual = "home"
                        }
                    )

                    "stats" -> StatsScreen(
                        onHomeClick = {
                            pantallaActual = "home"
                        },
                        onHabitsClick = {
                            pantallaActual = "habits"
                        },
                        onTipsClick = {
                            pantallaActual = "tips"
                        }
                    )

                    "habits" -> HabitsScreen(
                        onHomeClick = {
                            pantallaActual = "home"
                        },
                        onStatsClick = {
                            pantallaActual = "stats"
                        },
                        onTipsClick = {
                            pantallaActual = "tips"
                        }
                    )

                    "tips" -> TipsScreen(
                        onHomeClick = {
                            pantallaActual = "home"
                        },
                        onStatsClick = {
                            pantallaActual = "stats"
                        },
                        onHabitsClick = {
                            pantallaActual = "habits"
                        }
                    )
                }
            }
        }
    }
}