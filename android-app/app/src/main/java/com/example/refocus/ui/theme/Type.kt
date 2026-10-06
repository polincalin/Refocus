package com.example.refocus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.refocus.R

// Familia tipográfica Poppins
val PoppinsFontFamily = FontFamily(
    Font(
        resId = R.font.poppins_regular,
        weight = FontWeight.Normal
    ),
    Font(
        resId = R.font.poppins_medium,
        weight = FontWeight.Medium
    ),
    Font(
        resId = R.font.poppins_semibold,
        weight = FontWeight.SemiBold
    )
)

// Estilos predeterminados de Android,
// pero reemplazando la fuente por Poppins
private val DefaultTypography = Typography()

val Typography = Typography(

    displayLarge = DefaultTypography.displayLarge.copy(
        fontFamily = PoppinsFontFamily
    ),

    displayMedium = DefaultTypography.displayMedium.copy(
        fontFamily = PoppinsFontFamily
    ),

    displaySmall = DefaultTypography.displaySmall.copy(
        fontFamily = PoppinsFontFamily
    ),

    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFamily = PoppinsFontFamily
    ),

    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFamily = PoppinsFontFamily
    ),

    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontFamily = PoppinsFontFamily
    ),

    titleLarge = DefaultTypography.titleLarge.copy(
        fontFamily = PoppinsFontFamily
    ),

    titleMedium = DefaultTypography.titleMedium.copy(
        fontFamily = PoppinsFontFamily
    ),

    titleSmall = DefaultTypography.titleSmall.copy(
        fontFamily = PoppinsFontFamily
    ),

    bodyLarge = DefaultTypography.bodyLarge.copy(
        fontFamily = PoppinsFontFamily
    ),

    bodyMedium = DefaultTypography.bodyMedium.copy(
        fontFamily = PoppinsFontFamily
    ),

    bodySmall = DefaultTypography.bodySmall.copy(
        fontFamily = PoppinsFontFamily
    ),

    labelLarge = DefaultTypography.labelLarge.copy(
        fontFamily = PoppinsFontFamily
    ),

    labelMedium = DefaultTypography.labelMedium.copy(
        fontFamily = PoppinsFontFamily
    ),

    labelSmall = DefaultTypography.labelSmall.copy(
        fontFamily = PoppinsFontFamily
    )
)