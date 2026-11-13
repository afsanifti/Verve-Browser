package com.verve.browser.presentation.browser.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

internal val ExpressiveToolbarQuickSpring = spring<Float>(
    dampingRatio = 0.78f,
    stiffness = 500f
)

private val SideButtonSize = 56.dp
private val SideButtonGap = 8.dp
private val SideSlotFullWidth = SideButtonSize + SideButtonGap

@Composable
fun ExpressiveBottomToolbar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onTabSwitcherClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentUrl: String = "",
    isWebMode: Boolean = false,
    isCollapsed: Boolean = false,
    isSearchFocused: Boolean = false,
    bottomWindowInsets: WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit = {},
    onLeftActionClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val effectiveCollapsed = isWebMode && isCollapsed && !isSearchFocused
    val swipeThresholdPx = with(density) { 18.dp.toPx() }
    val sideSlideOffsetPx = with(density) { 16.dp.toPx() }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    val expandProgress by animateFloatAsState(
        targetValue = if (effectiveCollapsed) 0f else 1f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "toolbarExpandProgress"
    )

    val leftButtonProgress by animateFloatAsState(
        targetValue = if (isWebMode && !effectiveCollapsed && !isSearchFocused) 1f else 0f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "leftButtonProgress"
    )

    val rightButtonProgress by animateFloatAsState(
        targetValue = if (!effectiveCollapsed && !isSearchFocused) 1f else 0f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "rightButtonProgress"
    )

    val topPadding by animateDpAsState(
        targetValue = if (effectiveCollapsed) 4.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "toolbarTopPadding"
    )

    val bottomPadding by animateDpAsState(
        targetValue = if (effectiveCollapsed) 2.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 500f
        ),
        label = "toolbarBottomPadding"
    )

    val displayDomain = remember(currentUrl) { extractDisplayDomain(currentUrl) }
    val collapsedPillWidth = remember(displayDomain, density) {
        val measuredWidthPx = textMeasurer.measure(
            text = AnnotatedString(displayDomain.ifEmpty { "google.com" }),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        ).size.width
        val measuredWidthDp = with(density) { measuredWidthPx.toDp() } + 40.dp
        measuredWidthDp.coerceIn(112.dp, 220.dp)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomWindowInsets)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = topPadding,
                bottom = bottomPadding
            )
            .pointerInput(isWebMode, effectiveCollapsed, isSearchFocused) {
                if (!isWebMode || isSearchFocused) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { dragDelta = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragDelta += dragAmount
                        if (!effectiveCollapsed && dragDelta > swipeThresholdPx) {
                            onCollapsedChange(true)
                            dragDelta = 0f
                        } else if (effectiveCollapsed && dragDelta < -swipeThresholdPx) {
                            onCollapsedChange(false)
                            dragDelta = 0f
                        }
                    }
                )
            }
    ) {
        val clampedExpand = expandProgress.coerceAtLeast(0f)
        val clampedLeft = leftButtonProgress.coerceAtLeast(0f)
        val clampedRight = rightButtonProgress.coerceAtLeast(0f)

        val leftSlotWidth = SideSlotFullWidth * clampedLeft
        val rightSlotWidth = SideSlotFullWidth * clampedRight
        val targetExpandedPillWidth = (maxWidth - leftSlotWidth - rightSlotWidth)
            .coerceAtLeast(collapsedPillWidth)
        val currentPillWidth = lerp(
            start = collapsedPillWidth,
            stop = targetExpandedPillWidth,
            fraction = clampedExpand.coerceIn(0f, 1.03f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leftSlotWidth > 0.5.dp) {
                val leftAlpha = leftButtonProgress.coerceIn(0f, 1f)
                val leftScale = 0.72f + 0.28f * leftButtonProgress
                Box(
                    modifier = Modifier
                        .width(leftSlotWidth)
                        .padding(vertical = 10.dp)
                        .graphicsLayer {
                            alpha = leftAlpha
                            scaleX = leftScale
                            scaleY = leftScale
                            translationX = sideSlideOffsetPx * (1f - leftButtonProgress)
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveLeftActionButton(onClick = onLeftActionClick)
                        Spacer(modifier = Modifier.width(SideButtonGap))
                    }
                }
            }

            ExpressiveSearchPill(
                inputText = inputText,
                onInputTextChange = onInputTextChange,
                onUrlSubmitted = onUrlSubmitted,
                currentUrl = currentUrl,
                isWebMode = isWebMode,
                isCollapsed = effectiveCollapsed,
                expandProgress = expandProgress,
                onFocusChanged = onSearchFocusChanged,
                onExpandClick = { onCollapsedChange(false) },
                onSiteSettingsClick = onSiteSettingsClick,
                onRefreshClick = onRefreshClick,
                modifier = Modifier.width(currentPillWidth)
            )

            if (rightSlotWidth > 0.5.dp) {
                val rightAlpha = rightButtonProgress.coerceIn(0f, 1f)
                val rightScale = 0.72f + 0.28f * rightButtonProgress
                Box(
                    modifier = Modifier
                        .width(rightSlotWidth)
                        .padding(vertical = 10.dp)
                        .graphicsLayer {
                            alpha = rightAlpha
                            scaleX = rightScale
                            scaleY = rightScale
                            translationX = -sideSlideOffsetPx * (1f - rightButtonProgress)
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(SideButtonGap))
                        ExpressiveTabButton(onClick = onTabSwitcherClick)
                    }
                }
            }
        }
    }
}