package com.verve.browser.presentation.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeStyle(
    val title: String,
    val subtitle: String,
    val paletteStyle: PaletteStyle?
) {
    SYSTEM(
        title = "System theme",
        subtitle = "Standard Material You dynamic colors",
        paletteStyle = null
    ),
    TONAL_SPOT(
        title = "Tonal Spot",
        subtitle = "Balanced Material Expressive palette",
        paletteStyle = PaletteStyle.TonalSpot
    ),
    VIBRANT(
        title = "Vibrant",
        subtitle = "High-chroma expressive accents",
        paletteStyle = PaletteStyle.Vibrant
    ),
    EXPRESSIVE(
        title = "Expressive",
        subtitle = "Bold, multi-hue dynamic contrast",
        paletteStyle = PaletteStyle.Expressive
    ),
    MONOCHROME(
        title = "Monochrome",
        subtitle = "Clean grayscale neutral tones",
        paletteStyle = PaletteStyle.Monochrome
    ),
    FIDELITY(
        title = "Fidelity",
        subtitle = "True-to-source seed color accuracy",
        paletteStyle = PaletteStyle.Fidelity
    ),
    NEUTRAL(
        title = "Neutral",
        subtitle = "Subtle, low-saturation surfaces",
        paletteStyle = PaletteStyle.Neutral
    ),
    FRUIT_SALAD(
        title = "Fruit Salad",
        subtitle = "Playful two-tone accent palette",
        paletteStyle = PaletteStyle.FruitSalad
    ),
    RAINBOW(
        title = "Rainbow",
        subtitle = "Wide chromatic hue separation",
        paletteStyle = PaletteStyle.Rainbow
    )
}

enum class AppDarkMode(
    val title: String,
    val subtitle: String
) {
    SYSTEM(
        title = "System default",
        subtitle = "Match device dark mode setting"
    ),
    LIGHT(
        title = "Light",
        subtitle = "Always use light theme"
    ),
    DARK(
        title = "Dark",
        subtitle = "Always use dark theme"
    )
}

data class ThemeConfig(
    val style: AppThemeStyle = AppThemeStyle.SYSTEM,
    val darkMode: AppDarkMode = AppDarkMode.SYSTEM
) {
    fun summaryLabel(): String {
        return if (style == AppThemeStyle.SYSTEM && darkMode == AppDarkMode.SYSTEM) {
            "System default"
        } else {
            "${style.title} · ${darkMode.title}"
        }
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "verve_theme_prefs"
    private const val KEY_THEME_STYLE = "theme_style"
    private const val KEY_DARK_MODE = "dark_mode"

    private val _config = MutableStateFlow<ThemeConfig?>(null)
    val configFlow: StateFlow<ThemeConfig?> = _config.asStateFlow()

    /**
     * Call in Application.onCreate() / MainActivity.onCreate() so saved settings
     * are restored from disk before the first frame renders.
     */
    fun init(context: Context): ThemeConfig {
        return loadFromDisk(context)
    }

    fun getConfig(context: Context): ThemeConfig {
        return _config.value ?: loadFromDisk(context)
    }

    @Synchronized
    private fun loadFromDisk(context: Context): ThemeConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val styleName = prefs.getString(KEY_THEME_STYLE, AppThemeStyle.SYSTEM.name)
        val darkModeName = prefs.getString(KEY_DARK_MODE, AppDarkMode.SYSTEM.name)

        val loaded = ThemeConfig(
            style = AppThemeStyle.entries.firstOrNull { it.name == styleName } ?: AppThemeStyle.SYSTEM,
            darkMode = AppDarkMode.entries.firstOrNull { it.name == darkModeName } ?: AppDarkMode.SYSTEM
        )
        _config.value = loaded
        return loaded
    }

    @Synchronized
    fun setThemeStyle(context: Context, style: AppThemeStyle) {
        val current = getConfig(context)
        val updated = current.copy(style = style)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_STYLE, style.name)
            .commit()
        _config.value = updated
    }

    @Synchronized
    fun setDarkMode(context: Context, darkMode: AppDarkMode) {
        val current = getConfig(context)
        val updated = current.copy(darkMode = darkMode)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_MODE, darkMode.name)
            .commit()
        _config.value = updated
    }
}

@Composable
fun rememberThemeConfig(): ThemeConfig {
    val context = LocalContext.current
    val initial = remember(context) { ThemePreferences.getConfig(context) }
    val observed by ThemePreferences.configFlow.collectAsState()
    return observed ?: initial
}