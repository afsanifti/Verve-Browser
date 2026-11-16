package com.verve.browser.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.DynamicMaterialExpressiveTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerveBrowserTheme(
    siteSeedColor: Color? = null,
    isIncognito: Boolean = false,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val wallpaperSeedColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) {
            dynamicDarkColorScheme(context).primary
        } else {
            dynamicLightColorScheme(context).primary
        }
    } else {
        Color(0xFF6750A4)
    }

    val resolvedSeedColor = when {
        isIncognito -> Color(0xFF8E8E93)
        siteSeedColor != null -> siteSeedColor
        else -> wallpaperSeedColor
    }

    val resolvedStyle = when {
        isIncognito -> PaletteStyle.Monochrome
        siteSeedColor != null -> PaletteStyle.Vibrant
        else -> PaletteStyle.TonalSpot
    }

    DynamicMaterialExpressiveTheme(
        typography = AppTypography,
        seedColor = resolvedSeedColor,
        isDark = isDark,
        style = resolvedStyle,
        content = content
    )
}