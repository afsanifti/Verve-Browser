package com.verve.browser.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.DynamicMaterialExpressiveTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerveBrowserTheme(
    siteSeedColor: Color? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val latestContent by rememberUpdatedState(content)
    val preservedContent = remember {
        movableContentOf {
            latestContent()
        }
    }

    if (siteSeedColor != null) {
        WebsiteSnapshotThemeEngine(
            seedColor = siteSeedColor,
            isDark = isDark,
            content = preservedContent
        )
    } else {
        WallpaperThemeEngine(
            isDark = isDark,
            content = preservedContent
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebsiteSnapshotThemeEngine(
    seedColor: Color,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    DynamicMaterialExpressiveTheme(
        typography = AppTypography,
        seedColor = seedColor,
        isDark = isDark,
        style = PaletteStyle.Vibrant,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WallpaperThemeEngine(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val systemColorScheme = if (isDark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = systemColorScheme,
        typography = AppTypography,
        content = content
    )
}