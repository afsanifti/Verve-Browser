package com.verve.browser.presentation.browser.screen

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.max

@Immutable
internal data class BrowserInsetsState(
    val topSafeAreaInsets: WindowInsets,
    val topSafeAreaHeightDp: Dp,
    val bottomNavInsets: WindowInsets,
    val bottomNavInsetHeightDp: Dp
)

@Composable
internal fun rememberBrowserInsetsState(): BrowserInsetsState {
    val view = LocalView.current
    val density = LocalDensity.current

    val topSafeAreaInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val composeTopSafeAreaPx = topSafeAreaInsets.getTop(density)
    val rootTopSafeAreaPx = if (!view.isInEditMode) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            ?.top ?: 0
    } else {
        0
    }
    val topSafeAreaHeightDp = with(density) { max(composeTopSafeAreaPx, rootTopSafeAreaPx).toDp() }

    val bottomNavInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    val bottomNavInsetHeightDp = with(density) { bottomNavInsets.getBottom(density).toDp() }

    return BrowserInsetsState(
        topSafeAreaInsets = topSafeAreaInsets,
        topSafeAreaHeightDp = topSafeAreaHeightDp,
        bottomNavInsets = bottomNavInsets,
        bottomNavInsetHeightDp = bottomNavInsetHeightDp
    )
}

@Composable
internal fun BrowserSystemBarSync(
    isTabSwitcherOpen: Boolean,
    targetTopColor: Color,
    targetBottomColor: Color
) {
    val view = LocalView.current
    val isSystemLight = !isSystemInDarkTheme()

    if (!view.isInEditMode) {
        val isTopLight = if (isTabSwitcherOpen) isSystemLight else targetTopColor.luminance() > 0.5f
        val isBottomLight = if (isTabSwitcherOpen) isSystemLight else targetBottomColor.luminance() > 0.5f

        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = isTopLight
            controller.isAppearanceLightNavigationBars = isBottomLight
        }
    }
}