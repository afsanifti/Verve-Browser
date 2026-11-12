package com.verve.browser.presentation.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit = {},
    onLeftActionClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {}
) {
    val effectiveCollapsed = isWebMode && isCollapsed && !isSearchFocused
    val swipeThresholdPx = with(LocalDensity.current) { 18.dp.toPx() }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    val verticalPadding by animateDpAsState(
        targetValue = if (effectiveCollapsed) 8.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toolbarVerticalPadding"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = verticalPadding)
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
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Action Squircle Button (hidden when collapsed or while typing in search)
        AnimatedVisibility(
            visible = isWebMode && !effectiveCollapsed && !isSearchFocused,
            enter = fadeIn(tween(180)) + expandHorizontally(
                animationSpec = tween(220),
                expandFrom = Alignment.Start
            ),
            exit = fadeOut(tween(140)) + shrinkHorizontally(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Start
            )
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveLeftActionButton(onClick = onLeftActionClick)
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // Center Search / Domain Pill
        ExpressiveSearchPill(
            inputText = inputText,
            onInputTextChange = onInputTextChange,
            onUrlSubmitted = onUrlSubmitted,
            currentUrl = currentUrl,
            isWebMode = isWebMode,
            isCollapsed = effectiveCollapsed,
            onFocusChanged = onSearchFocusChanged,
            onExpandClick = { onCollapsedChange(false) },
            onSiteSettingsClick = onSiteSettingsClick,
            onRefreshClick = onRefreshClick,
            modifier = if (effectiveCollapsed) {
                Modifier
                    .wrapContentWidth()
                    .widthIn(min = 112.dp, max = 240.dp)
            } else {
                Modifier.weight(1f)
            }
        )

        // Right Tab Switcher Button (hidden when collapsed or while typing in search)
        AnimatedVisibility(
            visible = !effectiveCollapsed && !isSearchFocused,
            enter = fadeIn(tween(180)) + expandHorizontally(
                animationSpec = tween(220),
                expandFrom = Alignment.End
            ),
            exit = fadeOut(tween(140)) + shrinkHorizontally(
                animationSpec = tween(200),
                shrinkTowards = Alignment.End
            )
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveTabButton(onClick = onTabSwitcherClick)
            }
        }
    }
}