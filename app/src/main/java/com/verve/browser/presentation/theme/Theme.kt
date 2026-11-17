package com.verve.browser.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.DynamicMaterialExpressiveTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("OPT_IN_USAGE")
@Composable
fun VerveBrowserTheme(
    siteSeedColor: Color? = null,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeConfig = rememberThemeConfig()

    val isDark = when (themeConfig.darkMode) {
        AppDarkMode.SYSTEM -> isSystemInDarkTheme()
        AppDarkMode.LIGHT -> false
        AppDarkMode.DARK -> true
    }

    if (isIncognito) {
        DynamicMaterialExpressiveTheme(
            seedColor = Color(0xFF5F6368),
            typography = AppTypography,
            isDark = isDark,
            style = PaletteStyle.Monochrome,
            animate = true,
            content = content
        )
        return
    }

    val paletteStyle = themeConfig.style.paletteStyle

    if (paletteStyle == null) {
        val systemColorScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> darkColorScheme()
            else -> lightColorScheme()
        }

        MaterialTheme(
            colorScheme = systemColorScheme,
            typography = AppTypography,
            content = content
        )
    } else {
        val wallpaperSeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) {
                dynamicDarkColorScheme(context).primary
            } else {
                dynamicLightColorScheme(context).primary
            }
        } else {
            Color(0xFF6750A4)
        }

        DynamicMaterialExpressiveTheme(
            seedColor = siteSeedColor ?: wallpaperSeed,
            typography = AppTypography,
            isDark = isDark,
            style = paletteStyle,
            animate = true,
            content = content
        )
    }
}