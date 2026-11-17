package com.verve.browser.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.ktx.animateColorScheme
import com.materialkolor.material3.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerveBrowserTheme(
    siteSeedColor: Color? = null,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = rememberThemeConfig()

    val isDark = if (isIncognito) {
        true
    } else {
        when (themeConfig.darkMode) {
            AppDarkMode.SYSTEM -> isSystemInDarkTheme()
            AppDarkMode.LIGHT -> false
            AppDarkMode.DARK -> true
        }
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
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }
        }
        else -> {
            val wallpaperSeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) {
                    dynamicDarkColorScheme(context).primary
                } else {
                    dynamicLightColorScheme(context).primary
                }
            } else {
                Color(0xFF6750A4)
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