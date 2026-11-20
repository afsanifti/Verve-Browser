package com.verve.browser.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.ktx.animateColorScheme
import com.materialkolor.material3.rememberDynamicColorScheme

@Composable
fun VerveBrowserTheme(
    siteSeedColor: Color? = null,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = rememberThemeConfig()

    val isDark = isIncognito || when (themeConfig.darkMode) {
        AppDarkMode.SYSTEM -> isSystemInDarkTheme()
        AppDarkMode.LIGHT -> false
        AppDarkMode.DARK -> true
    }

    val paletteStyle = themeConfig.style.paletteStyle

    val targetColorScheme = when {
        isIncognito -> {
            rememberDynamicColorScheme(
                seedColor = Color(0xFF5F6368),
                isDark = true,
                style = PaletteStyle.Monochrome
            )
        }
        paletteStyle == null -> {
            if (siteSeedColor != null) {
                rememberDynamicColorScheme(
                    seedColor = siteSeedColor,
                    isDark = isDark,
                    style = PaletteStyle.TonalSpot
                )
            } else {
                run {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
            }
        }
        else -> {
            val wallpaperSeed =
                if (isDark) {
                    dynamicDarkColorScheme(context).primary
                } else {
                    dynamicLightColorScheme(context).primary
                }
            rememberDynamicColorScheme(
                seedColor = siteSeedColor ?: wallpaperSeed,
                isDark = isDark,
                style = paletteStyle
            )
        }
    }

    val animatedColorScheme = animateColorScheme(targetColorScheme)

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content
    )
}