package com.verve.browser.presentation.browser.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.browser.BrowserUiState
import kotlin.math.max

private val CollapsedToolbarTotalHeight = 35.dp
private val ExpandedToolbarTotalHeight = 92.dp

@Immutable
internal data class BrowserCurtainMetrics(
    val targetTopColor: Color,
    val targetBottomColor: Color,
    val animatedTopColor: Color,
    val animatedBottomColor: Color,
    val effectiveCurtainProgress: Float,
    val progressiveBottomCurtainHeightDp: Dp,
    val progressiveBottomShiftPx: Int
)

@Composable
internal fun rememberBrowserCurtainMetrics(
    uiState: BrowserUiState,
    isOnWebPage: Boolean,
    bottomNavInsetHeightDp: Dp,
    defaultRegionColor: Color
): BrowserCurtainMetrics {
    val density = LocalDensity.current

    val targetTopColor = if (isOnWebPage && !uiState.isSearchFocused) {
        uiState.topRegionColor ?: defaultRegionColor
    } else {
        defaultRegionColor
    }

    val targetBottomColor = if (isOnWebPage && !uiState.isSearchFocused) {
        uiState.bottomRegionColor ?: uiState.topRegionColor ?: defaultRegionColor
    } else {
        defaultRegionColor
    }

    val animatedTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "adaptiveTopRegionColor"
    )

    val animatedBottomColor by animateColorAsState(
        targetValue = targetBottomColor,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "adaptiveBottomRegionColor"
    )

    val isToolbarEffectivelyCollapsed = isOnWebPage && uiState.isToolbarCollapsed && !uiState.isSearchFocused
    val activeToolbarHeightDp by animateDpAsState(
        targetValue = if (isToolbarEffectivelyCollapsed) {
            CollapsedToolbarTotalHeight
        } else {
            ExpandedToolbarTotalHeight
        },
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "activeToolbarCurtainHeight"
    )

    val fixedBottomBarProgress by animateFloatAsState(
        targetValue = if (isOnWebPage && uiState.hasFixedBottomBar && !uiState.isSearchFocused) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "fixedBottomBarCurtainProgress"
    )

    val effectiveCurtainProgress = if (isOnWebPage && !uiState.isSearchFocused) {
        max(uiState.bottomCurtainProgress, fixedBottomBarProgress).coerceIn(0f, 1f)
    } else {
        0f
    }

    val fullBottomCurtainHeightDp = activeToolbarHeightDp + bottomNavInsetHeightDp
    val progressiveBottomCurtainHeightDp = fullBottomCurtainHeightDp * effectiveCurtainProgress
    val progressiveBottomShiftPx = with(density) { progressiveBottomCurtainHeightDp.roundToPx() }

    return BrowserCurtainMetrics(
        targetTopColor = targetTopColor,
        targetBottomColor = targetBottomColor,
        animatedTopColor = animatedTopColor,
        animatedBottomColor = animatedBottomColor,
        effectiveCurtainProgress = effectiveCurtainProgress,
        progressiveBottomCurtainHeightDp = progressiveBottomCurtainHeightDp,
        progressiveBottomShiftPx = progressiveBottomShiftPx
    )
}

@Composable
internal fun BrowserTopSafeAreaCurtain(
    topSafeAreaHeightDp: Dp,
    animatedTopColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topSafeAreaHeightDp)
            .background(animatedTopColor)
    )
}

@Composable
internal fun BrowserBottomProgressiveCurtain(
    curtainMetrics: BrowserCurtainMetrics,
    modifier: Modifier = Modifier
) {
    if (curtainMetrics.progressiveBottomCurtainHeightDp > 0.dp &&
        curtainMetrics.effectiveCurtainProgress > 0.001f
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(curtainMetrics.progressiveBottomCurtainHeightDp)
                .graphicsLayer { alpha = curtainMetrics.effectiveCurtainProgress }
                .background(curtainMetrics.animatedBottomColor)
        )
    }
}