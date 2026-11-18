package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

internal val ExpressiveToolbarQuickSpring = spring<Float>(
    dampingRatio = 0.98f,
    stiffness = 420f
)

private val ExpressiveToolbarDpSpring = spring<Dp>(
    dampingRatio = 1.0f,
    stiffness = 420f
)

// Critically damped curve for smooth, zero-bounce horizontal tab-swipe recovery
private val ExpressiveSwipeSpatialSpring = spring<Float>(
    dampingRatio = 0.98f,
    stiffness = 340f
)

private val SideButtonSize = 56.dp
private val SideButtonMinCompressedWidth = 36.dp
private val SideButtonMaxCompressDp = SideButtonSize - SideButtonMinCompressedWidth // 20.dp
private val SideButtonGap = 8.dp
private val SideSlotFullWidth = SideButtonSize + SideButtonGap

@Composable
fun ExpressiveBottomToolbar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onTabSwitcherClick: () -> Unit,
    isThemeLoading: Boolean,
    onThemeAnimationCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    currentUrl: String = "",
    isWebMode: Boolean = false,
    isCollapsed: Boolean = false,
    isSearchFocused: Boolean = false,
    isPageLoading: Boolean = false,
    isIncognito: Boolean = false,
    canSwipeToLeftTab: Boolean = false,
    canSwipeToRightTab: Boolean = false,
    bottomWindowInsets: WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit = {},
    onSwipeStarted: () -> Unit = {},
    onSwipeFractionChanged: (Float) -> Unit = {},
    onSwipeCommitted: (direction: Int, currentFraction: Float) -> Unit = { _, _ -> },
    onSwipeCancelled: () -> Unit = {},
    onSwipeToLeftTab: () -> Unit = {},
    onSwipeToRightTab: () -> Unit = {},
    onToggleIncognito: () -> Unit = {},
    onLeftActionClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onStopLoadingClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val effectiveCollapsed = isWebMode && isCollapsed && !isSearchFocused
    val verticalSwipeThresholdPx = with(density) { 18.dp.toPx() }
    val horizontalCommitThresholdPx = with(density) { 54.dp.toPx() }
    val sideSlideOffsetPx = with(density) { 16.dp.toPx() }
    val horizontalOuterPaddingPx = with(density) { 32.dp.toPx() }

    val currentIsWebMode by rememberUpdatedState(isWebMode)
    val currentEffectiveCollapsed by rememberUpdatedState(effectiveCollapsed)
    val currentIsSearchFocused by rememberUpdatedState(isSearchFocused)
    val currentCanSwipeLeftTab by rememberUpdatedState(canSwipeToLeftTab)
    val currentCanSwipeRightTab by rememberUpdatedState(canSwipeToRightTab)
    val currentOnCollapsedChange by rememberUpdatedState(onCollapsedChange)
    val currentOnSwipeStarted by rememberUpdatedState(onSwipeStarted)
    val currentOnSwipeFractionChanged by rememberUpdatedState(onSwipeFractionChanged)
    val currentOnSwipeCommitted by rememberUpdatedState(onSwipeCommitted)
    val currentOnSwipeCancelled by rememberUpdatedState(onSwipeCancelled)
    val currentOnSwipeToLeftTab by rememberUpdatedState(onSwipeToLeftTab)
    val currentOnSwipeToRightTab by rememberUpdatedState(onSwipeToRightTab)

    // -1f = full swipe left (compresses left button / glides pill left),
    // +1f = full swipe right (compresses right button / glides pill right)
    val swipeProgress = remember { Animatable(0f) }

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
        animationSpec = ExpressiveToolbarDpSpring,
        label = "toolbarTopPadding"
    )

    val bottomPadding by animateDpAsState(
        targetValue = if (effectiveCollapsed) 2.dp else 10.dp,
        animationSpec = ExpressiveToolbarDpSpring,
        label = "toolbarBottomPadding"
    )

    val displayDomain = remember(currentUrl) { extractDisplayDomain(currentUrl) }
    val targetCollapsedPillWidth = remember(displayDomain, density) {
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

    val collapsedPillWidth by animateDpAsState(
        targetValue = targetCollapsedPillWidth,
        animationSpec = ExpressiveToolbarDpSpring,
        label = "collapsedPillWidth"
    )

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
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentIsSearchFocused) return@awaitEachGesture

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var totalDx = 0f
                    var totalDy = 0f
                    var verticalDragDelta = 0f
                    var lockedAxis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
                    var crossedCommitHaptic = false
                    var lastRawFraction = 0f

                    val completed = drag(down.id) { change ->
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        totalDx += delta.x
                        totalDy += delta.y

                        if (lockedAxis == 0) {
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                if (abs(totalDx) > abs(totalDy) * 1.15f) {
                                    lockedAxis = 1
                                    coroutineScope.launch { swipeProgress.stop() }
                                    currentOnSwipeStarted()
                                } else if (currentIsWebMode) {
                                    lockedAxis = 2
                                }
                            }
                        }

                        if (lockedAxis == 1) {
                            change.consume()
                            val isSwipingRight = totalDx > 0f
                            val hasTargetTab = if (isSwipingRight) {
                                currentCanSwipeLeftTab
                            } else {
                                currentCanSwipeRightTab
                            }

                            // 1:1 viewport fraction for the Chrome-style page carousel
                            val viewportWidthPx = (size.width.toFloat() + horizontalOuterPaddingPx)
                                .coerceAtLeast(1f)
                            val edgeResistance = if (hasTargetTab) 1f else 0.16f
                            lastRawFraction = ((totalDx / viewportWidthPx) * edgeResistance)
                                .coerceIn(-1f, 1f)
                            currentOnSwipeFractionChanged(lastRawFraction)

                            // Smooth asymptotic curve for side button compression
                            val normalized = (abs(totalDx) / (horizontalCommitThresholdPx * 1.35f))
                                .coerceAtLeast(0f)
                            val easedMagnitude = 1f - exp(-1.45f * normalized)
                            val pillResistance = if (hasTargetTab) 1f else 0.32f
                            val targetProgress = (if (isSwipingRight) 1f else -1f) *
                                    easedMagnitude * pillResistance

                            if (hasTargetTab) {
                                val isPastCommit = abs(totalDx) >= horizontalCommitThresholdPx
                                if (isPastCommit && !crossedCommitHaptic) {
                                    crossedCommitHaptic = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (!isPastCommit && crossedCommitHaptic) {
                                    crossedCommitHaptic = false
                                }
                            }

                            coroutineScope.launch {
                                swipeProgress.snapTo(targetProgress)
                            }
                        } else if (lockedAxis == 2 && currentIsWebMode) {
                            change.consume()
                            verticalDragDelta += delta.y
                            if (!currentEffectiveCollapsed && verticalDragDelta > verticalSwipeThresholdPx) {
                                currentOnCollapsedChange(true)
                                verticalDragDelta = 0f
                            } else if (currentEffectiveCollapsed && verticalDragDelta < -verticalSwipeThresholdPx) {
                                currentOnCollapsedChange(false)
                                verticalDragDelta = 0f
                            }
                        }
                    }

                    if (lockedAxis == 1) {
                        val velocityX = velocityTracker.calculateVelocity().x
                        val isSwipingRight = totalDx > 0f
                        val hasTargetTab = if (isSwipingRight) {
                            currentCanSwipeLeftTab
                        } else {
                            currentCanSwipeRightTab
                        }

                        val isFastFling = if (isSwipingRight) {
                            velocityX > 650f && totalDx > touchSlop
                        } else {
                            velocityX < -650f && totalDx < -touchSlop
                        }

                        val shouldCommit = completed && hasTargetTab &&
                                (abs(totalDx) >= horizontalCommitThresholdPx || isFastFling)

                        if (shouldCommit) {
                            if (!crossedCommitHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            val direction = if (isSwipingRight) 1 else -1
                            currentOnSwipeCommitted(direction, lastRawFraction)
                            if (isSwipingRight) {
                                currentOnSwipeToLeftTab()
                            } else {
                                currentOnSwipeToRightTab()
                            }
                        } else {
                            currentOnSwipeCancelled()
                        }

                        coroutineScope.launch {
                            swipeProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = ExpressiveSwipeSpatialSpring
                            )
                        }
                    }
                }
            }
    ) {
        val clampedExpand = expandProgress.coerceIn(0f, 1f)
        val collapsedFactor = 1f - clampedExpand
        val clampedLeft = leftButtonProgress.coerceAtLeast(0f)
        val clampedRight = rightButtonProgress.coerceAtLeast(0f)
        val currentSwipe = swipeProgress.value

        val leftCompressDp = SideButtonMaxCompressDp * (-currentSwipe).coerceIn(0f, 1f) * clampedLeft
        val rightCompressDp = SideButtonMaxCompressDp * currentSwipe.coerceIn(0f, 1f) * clampedRight

        val leftButtonWidth = (SideButtonSize - leftCompressDp).coerceAtLeast(SideButtonMinCompressedWidth)
        val rightButtonWidth = (SideButtonSize - rightCompressDp).coerceAtLeast(SideButtonMinCompressedWidth)

        val leftSlotWidth = (SideSlotFullWidth - leftCompressDp) * clampedLeft
        val rightSlotWidth = (SideSlotFullWidth - rightCompressDp) * clampedRight

        val targetExpandedPillWidth = (maxWidth - leftSlotWidth - rightSlotWidth)
            .coerceAtLeast(collapsedPillWidth)
        val currentPillWidth = lerp(
            start = collapsedPillWidth,
            stop = targetExpandedPillWidth,
            fraction = expandProgress.coerceIn(0f, 1f)
        )

        val collapsedPillTranslationPx = with(density) {
            (28.dp * currentSwipe * collapsedFactor).toPx()
        }

        val noLeftButtonEdgeNudgePx = if (clampedLeft < 0.05f && currentSwipe < 0f) {
            with(density) { (12.dp * currentSwipe * clampedExpand).toPx() }
        } else {
            0f
        }
        val collapsedInnerParallaxPx = with(density) {
            (8.dp * currentSwipe * collapsedFactor).toPx()
        }
        val contentShiftPx = with(density) {
            ((rightCompressDp - leftCompressDp) * 0.45f).toPx()
        } + noLeftButtonEdgeNudgePx + collapsedInnerParallaxPx

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
                        ExpressiveLeftActionButton(
                            onClick = onLeftActionClick,
                            buttonWidth = leftButtonWidth
                        )
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
                isPageLoading = isPageLoading,
                isThemeLoading = isThemeLoading,
                isIncognito = isIncognito,
                expandProgress = expandProgress,
                swipeContentShiftPx = contentShiftPx,
                onThemeAnimationCompleted = onThemeAnimationCompleted,
                onFocusChanged = onSearchFocusChanged,
                onExpandClick = { onCollapsedChange(false) },
                onSiteSettingsClick = onSiteSettingsClick,
                onRefreshClick = onRefreshClick,
                onStopLoadingClick = onStopLoadingClick,
                onToggleIncognito = onToggleIncognito,
                modifier = Modifier
                    .width(currentPillWidth)
                    .graphicsLayer {
                        translationX = collapsedPillTranslationPx
                    }
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
                        ExpressiveTabButton(
                            onClick = onTabSwitcherClick,
                            buttonWidth = rightButtonWidth
                        )
                    }
                }
            }
        }
    }
}