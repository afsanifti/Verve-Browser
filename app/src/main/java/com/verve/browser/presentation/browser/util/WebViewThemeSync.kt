package com.verve.browser.presentation.browser.util

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.verve.browser.presentation.theme.AppDarkMode

object WebViewThemeSync {

    fun resolveIsDarkTheme(darkMode: AppDarkMode, isSystemDark: Boolean): Boolean {
        return when (darkMode) {
            AppDarkMode.DARK -> true
            AppDarkMode.LIGHT -> false
            AppDarkMode.SYSTEM -> isSystemDark
        }
    }

    /**
     * Syncs the application night mode with Verve's theme setting so WebView
     * advertises the matching CSS `prefers-color-scheme` to websites.
     */
    fun applyAppNightMode(context: Context, darkMode: AppDarkMode) {
        val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
        val nightMode = when (darkMode) {
            AppDarkMode.DARK -> UiModeManager.MODE_NIGHT_YES
            AppDarkMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            AppDarkMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        }
        uiModeManager.setApplicationNightMode(nightMode)
    }

    /**
     * Configures a WebView instance so websites use their native dark theme
     * (`prefers-color-scheme: dark`) when [isDarkTheme] is true, without force-inverting
     * light-only websites.
     */
    @Suppress("DEPRECATION")
    fun applyToWebView(webView: WebView, isDarkTheme: Boolean) {
        val settings = webView.settings

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            }
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                )
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    settings,
                    if (isDarkTheme) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                )
            }
        }
    }
}