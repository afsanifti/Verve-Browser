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

// Intermediate anti-halation variable weights for dark-mode clarity
val FontWeightSpaceHero = FontWeight(425)
val FontWeightBrowserMedium = FontWeight(450)
val FontWeightBrowserLabel = FontWeight(475)

@OptIn(ExperimentalTextApi::class)
internal fun variableFontFamily(
    rond: Float = 85f,
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
        buildFont(FontWeight.Normal),       // 400
        buildFont(FontWeightSpaceHero),     // 425
        buildFont(FontWeightBrowserMedium), // 450
        buildFont(FontWeightBrowserLabel),  // 475
        buildFont(FontWeight.Medium),       // 500
        buildFont(FontWeight.SemiBold),     // 600
        buildFont(FontWeight.Bold)          // 700
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 57f, wdth = 100f),
        fontWeight = FontWeightSpaceHero,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 45f, wdth = 100f),
        fontWeight = FontWeightSpaceHero,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    // display-lg-mobile: 32sp / 38sp • wght 425 • ROND 95 • opsz 32 (Space & landing titles)
    displaySmall = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 32f, wdth = 100f),
        fontWeight = FontWeightSpaceHero,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 32f, wdth = 100f),
        fontWeight = FontWeightSpaceHero,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    // headline-md: 22sp / 28sp • wght 450 • ROND 95 • opsz 24 (Top app bar titles)
    headlineMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 24f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 24f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 22f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // title-md: 16sp / 22sp • wght 450 • ROND 85 • opsz 16 (File names & settings rows)
    titleMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 16f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 16f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp
    ),
    // body-md: 14sp / 20sp • wght 400 • ROND 85 • opsz 14 (Supporting metadata & values)
    bodyMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 12f, wdth = 100f),
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 14f, wdth = 100f),
        fontWeight = FontWeightBrowserLabel,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // label-md: 12sp / 16sp • wght 475 • ROND 95 • opsz 12 (Filter chips, section labels & URL pills)
    labelMedium = TextStyle(
        fontFamily = variableFontFamily(rond = 95f, grad = 0f, opsz = 12f, wdth = 100f),
        fontWeight = FontWeightBrowserLabel,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.35.sp
    ),
    // mono-sm role: 11sp / 14sp • tabular-nums (Tabular file sizes & counters)
    labelSmall = TextStyle(
        fontFamily = variableFontFamily(rond = 85f, grad = 0f, opsz = 11f, wdth = 100f),
        fontWeight = FontWeightBrowserMedium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = "tnum"
    )
)

// Dedicated tabular style for progress percentages, file sizes, and download counters
val AppTabularNumbersStyle = AppTypography.labelSmall.copy(
    fontFeatureSettings = "tnum"
)