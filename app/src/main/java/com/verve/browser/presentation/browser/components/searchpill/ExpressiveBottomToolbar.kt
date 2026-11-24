package com.verve.browser.presentation.browser.components.searchpill

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val ExpressiveToolbarQuickSpring = spring<Float>(
    dampingRatio = 0.94f,
    stiffness = 340f
)

private val ExpressiveToolbarDpSpring = spring<Dp>(
    dampingRatio = 0.96f,
    stiffness = 360f
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
    isFindInPage: Boolean = false,
    findQuery: String = "",
    findActiveMatchOrdinal: Int = 0,
    findTotalMatches: Int = 0,
    isPageLoading: Boolean = false,
    isIncognito: Boolean = false,
    isReaderMode: Boolean = false,
    canSwipeToLeftTab: Boolean = false,
    canSwipeToRightTab: Boolean = false,
    bottomWindowInsets: WindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit = {},
    onFindQueryChange: (String) -> Unit = {},
    onFindPrevious: () -> Unit = {},
    onFindNext: () -> Unit = {},
    onCloseFindInPage: () -> Unit = {},
    onSwipeStarted: () -> Unit = {},
    onSwipeFractionChanged: (Float) -> Unit = {},
    onSwipeCommitted: (direction: Int, currentFraction: Float) -> Unit = { _, _ -> },
    onSwipeCancelled: () -> Unit = {},
    onSwipeToLeftTab: () -> Unit = {},
    onSwipeToRightTab: () -> Unit = {},
    onToggleIncognito: () -> Unit = {},
    onLeftActionClick: () -> Unit = {},
    onSiteSettingsClick: () -> Unit = {},
    onExitReaderModeClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onStopLoadingClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val effectiveCollapsed = isWebMode && isCollapsed && !isSearchFocused && !isFindInPage
    val isSingleFullPill = isSearchFocused || isFindInPage
    val verticalSwipeThresholdPx = with(density) { 18.dp.toPx() }
    val horizontalCommitThresholdPx = with(density) { 54.dp.toPx() }
    val sideTuckSlidePx = with(density) { 26.dp.toPx() }
    val horizontalOuterPaddingPx = with(density) { 32.dp.toPx() }

    val currentIsWebMode by rememberUpdatedState(isWebMode)
    val currentEffectiveCollapsed by rememberUpdatedState(effectiveCollapsed)
    val currentIsSingleFullPill by rememberUpdatedState(isSingleFullPill)
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

    // Master collapse/expand progress: 0f = collapsed, 1f = expanded
    val expandProgress by animateFloatAsState(
        targetValue = if (effectiveCollapsed) 0f else 1f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "toolbarExpandProgress"
    )

    val webModeFactor by animateFloatAsState(
        targetValue = if (isWebMode) 1f else 0f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "toolbarWebModeFactor"
    )

    val singlePillFactor by animateFloatAsState(
        targetValue = if (isSingleFullPill) 1f else 0f,
        animationSpec = ExpressiveToolbarQuickSpring,
        label = "toolbarSinglePillFactor"
    )

    val clampedExpand = expandProgress.coerceIn(0f, 1f)
    val topPadding = lerp(4.dp, 10.dp, clampedExpand)
    val bottomPadding = lerp(2.dp, 10.dp, clampedExpand)

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

    val collapsedFactor = 1f - clampedExpand
    val activeSideFactor = (1f - singlePillFactor.coerceIn(0f, 1f))
    val leftVisibility = (clampedExpand * webModeFactor.coerceIn(0f, 1f) * activeSideFactor).coerceIn(0f, 1f)
    val rightVisibility = (clampedExpand * activeSideFactor).coerceIn(0f, 1f)
    val effectiveLeftReserveFactor = maxOf(leftVisibility, rightVisibility)

    val currentSwipe = swipeProgress.value
    val leftCompressDp = SideButtonMaxCompressDp * (-currentSwipe).coerceIn(0f, 1f) * leftVisibility
    val rightCompressDp = SideButtonMaxCompressDp * currentSwipe.coerceIn(0f, 1f) * rightVisibility

    val leftButtonWidth = (SideButtonSize - leftCompressDp).coerceAtLeast(SideButtonMinCompressedWidth)
    val rightButtonWidth = (SideButtonSize - rightCompressDp).coerceAtLeast(SideButtonMinCompressedWidth)

    val collapsedPillTranslationPx = with(density) {
        (28.dp * currentSwipe * collapsedFactor).toPx()
    }
    val noLeftButtonEdgeNudgePx = if (leftVisibility < 0.05f && currentSwipe < 0f) {
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

    val gestureModifier = Modifier
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
                if (currentIsSingleFullPill) return@awaitEachGesture

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

                        val viewportWidthPx = (size.width.toFloat() + horizontalOuterPaddingPx)
                            .coerceAtLeast(1f)
                        val edgeResistance = if (hasTargetTab) 1f else 0.16f
                        lastRawFraction = ((totalDx / viewportWidthPx) * edgeResistance)
                            .coerceIn(-1f, 1f)
                        currentOnSwipeFractionChanged(lastRawFraction)

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

    Layout(
        modifier = modifier.then(gestureModifier),
        content = {
            // Child 0: Left Action Button (keeps full 56.dp geometry during collapse/expand)
            Box {
                if (leftVisibility > 0.01f) {
                    ExpressiveLeftActionButton(
                        onClick = onLeftActionClick,
                        buttonWidth = leftButtonWidth
                    )
                }
            }

            // Child 1: Center Search / URL Pill
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
                isReaderMode = isReaderMode,
                isFindInPage = isFindInPage,
                findQuery = findQuery,
                findActiveMatchOrdinal = findActiveMatchOrdinal,
                findTotalMatches = findTotalMatches,
                expandProgress = clampedExpand,
                swipeContentShiftPx = contentShiftPx,
                onThemeAnimationCompleted = onThemeAnimationCompleted,
                onFocusChanged = onSearchFocusChanged,
                onExpandClick = { onCollapsedChange(false) },
                onSiteSettingsClick = onSiteSettingsClick,
                onExitReaderModeClick = onExitReaderModeClick,
                onRefreshClick = onRefreshClick,
                onStopLoadingClick = onStopLoadingClick,
                onToggleIncognito = onToggleIncognito,
                onFindQueryChange = onFindQueryChange,
                onFindPrevious = onFindPrevious,
                onFindNext = onFindNext,
                onCloseFindInPage = onCloseFindInPage
            )

            // Child 2: Right Tab Switcher Button (keeps full 56.dp geometry during collapse/expand)
            Box {
                if (rightVisibility > 0.01f) {
                    ExpressiveTabButton(
                        onClick = onTabSwitcherClick,
                        buttonWidth = rightButtonWidth
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val maxWidthPx = constraints.maxWidth
        val sideGapPx = SideButtonGap.roundToPx()
        val sideSlotFullPx = SideSlotFullWidth.roundToPx()
        val leftCompressPx = leftCompressDp.roundToPx()
        val rightCompressPx = rightCompressDp.roundToPx()

        val leftSlotPx = ((sideSlotFullPx - leftCompressPx) * effectiveLeftReserveFactor).roundToInt()
        val rightSlotPx = ((sideSlotFullPx - rightCompressPx) * rightVisibility).roundToInt()

        val collapsedPillWidthPx = collapsedPillWidth.roundToPx().coerceAtMost(maxWidthPx)
        val expandedPillWidthPx = (maxWidthPx - leftSlotPx - rightSlotPx)
            .coerceAtLeast(collapsedPillWidthPx)
        val pillWidthPx = lerp(collapsedPillWidthPx, expandedPillWidthPx, clampedExpand)

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val leftPlaceable = measurables[0].measure(looseConstraints)
        val pillPlaceable = measurables[1].measure(
            Constraints.fixedWidth(pillWidthPx).copy(minHeight = 0, maxHeight = constraints.maxHeight)
        )
        val rightPlaceable = measurables[2].measure(looseConstraints)

        val sideHeightPx = maxOf(leftPlaceable.height, rightPlaceable.height)
        val expandedRowHeightPx = maxOf(pillPlaceable.height, sideHeightPx)
        val layoutHeightPx = lerp(
            pillPlaceable.height,
            expandedRowHeightPx,
            maxOf(leftVisibility, rightVisibility)
        )

        val totalContentPx = leftSlotPx + pillWidthPx + rightSlotPx
        val startX = (maxWidthPx - totalContentPx) / 2
        val pillX = startX + leftSlotPx
        val pillY = (layoutHeightPx - pillPlaceable.height) / 2

        layout(width = maxWidthPx, height = layoutHeightPx) {
            if (leftVisibility > 0.01f && leftPlaceable.width > 0) {
                val leftAlpha = ((leftVisibility - 0.14f) / 0.86f).coerceIn(0f, 1f)
                val leftScale = 0.76f + 0.24f * leftVisibility
                val leftX = pillX - sideGapPx - leftPlaceable.width
                val leftY = (layoutHeightPx - leftPlaceable.height) / 2
                leftPlaceable.placeWithLayer(
                    x = leftX,
                    y = leftY,
                    zIndex = 0f
                ) {
                    alpha = leftAlpha
                    scaleX = leftScale
                    scaleY = leftScale
                    translationX = sideTuckSlidePx * (1f - leftVisibility)
                }
            }

            if (rightVisibility > 0.01f && rightPlaceable.width > 0) {
                val rightAlpha = ((rightVisibility - 0.14f) / 0.86f).coerceIn(0f, 1f)
                val rightScale = 0.76f + 0.24f * rightVisibility
                val rightX = pillX + pillWidthPx + sideGapPx
                val rightY = (layoutHeightPx - rightPlaceable.height) / 2
                rightPlaceable.placeWithLayer(
                    x = rightX,
                    y = rightY,
                    zIndex = 0f
                ) {
                    alpha = rightAlpha
                    scaleX = rightScale
                    scaleY = rightScale
                    translationX = -sideTuckSlidePx * (1f - rightVisibility)
                }
            }

            pillPlaceable.placeWithLayer(
                x = pillX,
                y = pillY,
                zIndex = 1f
            ) {
                translationX = collapsedPillTranslationPx
            }
        }
    }
}