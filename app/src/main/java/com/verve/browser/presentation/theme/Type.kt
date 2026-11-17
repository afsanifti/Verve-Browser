package com.verve.browser.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.verve.browser.R

@OptIn(ExperimentalTextApi::class)
internal fun variableFontFamily(
    rond: Float = 0f,
    grad: Float = 0f,
    opsz: Float = 14f,
    wdth: Float = 100f
): FontFamily {
    fun buildFont(weight: FontWeight) = Font(
        resId = R.font.google_sans_flex,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.opticalSizing(opsz.sp),
            FontVariation.width(wdth),
            FontVariation.Setting("ROND", rond),
            FontVariation.Setting("GRAD", grad)
        )
    )

    return FontFamily(
        buildFont(FontWeight.Normal),
        buildFont(FontWeight.Medium),
        buildFont(FontWeight.SemiBold),
        buildFont(FontWeight.Bold)
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 0f, opsz = 57f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 0f, opsz = 45f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = variableFontFamily(rond = 80f, grad = 0f, opsz = 36f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 80f, grad = 0f, opsz = 32f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 80f, grad = 0f, opsz = 28f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 0f, opsz = 24f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 0f, opsz = 22f, wdth = 100f),
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 0f, opsz = 16f, wdth = 100f),
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 50f, grad = 0f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 25f, grad = 0f, opsz = 16f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 0f, grad = 0f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = variableFontFamily(rond = 0f, grad = 0f, opsz = 12f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 15f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 100f, grad = 15f, opsz = 12f, wdth = 100f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 50f, grad = 10f, opsz = 11f, wdth = 100f),
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)