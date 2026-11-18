package com.verve.browser.presentation.browser.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.TabStack
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Critically damped curves so cards glide smoothly into place without bouncing
private val SmoothStackSpring = spring<Float>(
    dampingRatio = 0.98f,
    stiffness = 310f
)

private val SmoothBiasDpSpring = spring<Dp>(
    dampingRatio = 1.0f,
    stiffness = 310f
)

private val DismissExitSpec = tween<Float>(
    durationMillis = 190,
    easing = FastOutLinearInEasing
)

private enum class DragAxis {
    Undecided,
    Horizontal,
    Vertical
}

@Composable
internal fun QuickTabsCardStack(
    tabs: List<BrowserTab>,
    allTabsInOrder: List<BrowserTab>,
    activeStack: TabStack?,
    thumbnailProvider: (String) -> ImageBitmap?,
    cardWidth: Dp,
    cardHeight: Dp,
    onFocusTab: (String) -> Unit,
    onOpenFocusedTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (allTabsInOrder.isEmpty()) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentTabs by rememberUpdatedState(allTabsInOrder)
    val currentOnFocusTab by rememberUpdatedState(onFocusTab)
    val currentOnOpenFocusedTab by rememberUpdatedState(onOpenFocusedTab)
    val currentOnCloseTab by rememberUpdatedState(onCloseTab)

    val focusedId = tabs.firstOrNull()?.id ?: allTabsInOrder.first().id
    val initialIndex = remember {
        allTabsInOrder.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
    }

    val scrollPosition = remember { Animatable(initialIndex.toFloat()) }
    val dismissOffsetY = remember { Animatable(0f) }
    val postCloseShiftDelta = remember { Animatable(0f) }

    var lastHapticIndex by remember { mutableIntStateOf(initialIndex) }
    var previousTabCount by remember { mutableIntStateOf(allTabsInOrder.size) }
    var closingTabId by remember { mutableStateOf<String?>(null) }
    var closedFromIndex by remember { mutableIntStateOf(-1) }

    val cardWidthPx = with(density) { cardWidth.toPx() }.coerceAtLeast(1f)
    val cardHeightPx = with(density) { cardHeight.toPx() }.coerceAtLeast(1f)
    val cardGapPx = with(density) { 26.dp.toPx() }
    val fanSpreadPx = with(density) { 82.dp.toPx() }
    val dragSensitivityPx = (cardWidthPx * 0.88f).coerceAtLeast(1f)
    val dismissThresholdPx = with(density) { 110.dp.toPx() }

    val currentPos = scrollPosition.value
    val maxIndex = (allTabsInOrder.size - 1).coerceAtLeast(0)
    val nearestIndex = currentPos.roundToInt().coerceIn(0, maxIndex)

    // Handle external tab removals and new tab insertion at the far right (index 0)
    LaunchedEffect(allTabsInOrder) {
        val prevCount = previousTabCount
        previousTabCount = allTabsInOrder.size

        if (allTabsInOrder.size < prevCount && closingTabId == null) {
            val newMaxIdx = (allTabsInOrder.size - 1).coerceAtLeast(0)
            val targetIdx = scrollPosition.value.roundToInt().coerceIn(0, newMaxIdx)
            lastHapticIndex = targetIdx
            allTabsInOrder.getOrNull(targetIdx)?.let { nextTab ->
                currentOnFocusTab(nextTab.id)
            }
            scrollPosition.snapTo(targetIdx.toFloat())
            return@LaunchedEffect
        }

        // When a new tab is added at index 0 (far right), glide it smoothly into focus from the right
        if (allTabsInOrder.size > prevCount && closingTabId == null) {
            val addedCount = allTabsInOrder.size - prevCount
            val shiftedPos = (scrollPosition.value + addedCount).coerceIn(0f, maxIndex.toFloat())
            scrollPosition.snapTo(shiftedPos)
            lastHapticIndex = 0
            allTabsInOrder.firstOrNull()?.let { newestTab ->
                currentOnFocusTab(newestTab.id)
            }
            scrollPosition.animateTo(0f, SmoothStackSpring)
            return@LaunchedEffect
        }

        val targetIdx = allTabsInOrder.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
        if (closingTabId == null && !scrollPosition.isRunning && nearestIndex != targetIdx) {
            lastHapticIndex = targetIdx
            scrollPosition.animateTo(targetIdx.toFloat(), SmoothStackSpring)
        }
    }

    // Emit subtle haptic ticks and update focused tab as cards glide past center
    LaunchedEffect(nearestIndex, allTabsInOrder, closingTabId) {
        if (closingTabId != null) return@LaunchedEffect
        if (nearestIndex != lastHapticIndex) {
            lastHapticIndex = nearestIndex
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val centeredTab = allTabsInOrder.getOrNull(nearestIndex)
        if (centeredTab != null && centeredTab.id != focusedId) {
            currentOnFocusTab(centeredTab.id)
        }
    }

    // Smooth optical right bias so the centered card + left-peeking stack stay balanced
    val remainingLeftStackCount = (maxIndex - nearestIndex).coerceAtLeast(0)
    val targetBiasX = when (remainingLeftStackCount) {
        0 -> 0.dp
        1 -> 12.dp
        else -> 18.dp
    }
    val animatedBiasX by animateDpAsState(
        targetValue = targetBiasX,
        animationSpec = SmoothBiasDpSpring,
        label = "smoothStackBiasX"
    )
    val animatedBiasPx = with(density) { animatedBiasX.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(allTabsInOrder.size, cardWidthPx, cardHeightPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (closingTabId != null) return@awaitEachGesture

                    coroutineScope.launch {
                        scrollPosition.stop()
                        dismissOffsetY.stop()
                        postCloseShiftDelta.stop()
                        postCloseShiftDelta.snapTo(0f)
                    }

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var axis = DragAxis.Undecided
                    var totalDx = 0f
                    var totalDy = 0f
                    val dragStartPos = scrollPosition.value

                    val completed = drag(down.id) { change ->
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        totalDx += delta.x
                        totalDy += delta.y

                        if (axis == DragAxis.Undecided) {
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                axis = if (abs(totalDx) >= abs(totalDy)) {
                                    DragAxis.Horizontal
                                } else {
                                    DragAxis.Vertical
                                }
                            }
                        }

                        when (axis) {
                            DragAxis.Horizontal -> {
                                change.consume()
                                val currentMax = (currentTabs.size - 1).coerceAtLeast(0).toFloat()
                                val cur = scrollPosition.value
                                val isOverscrolling = (cur < 0f && delta.x < 0f) ||
                                        (cur > currentMax && delta.x > 0f)
                                val resistance = if (isOverscrolling) 0.34f else 1f
                                val nextPos = cur + (delta.x / dragSensitivityPx) * resistance
                                coroutineScope.launch {
                                    scrollPosition.snapTo(nextPos)
                                }
                            }
                            DragAxis.Vertical -> {
                                if (dismissOffsetY.value + delta.y <= 0f) {
                                    change.consume()
                                    val nextY = (dismissOffsetY.value + delta.y)
                                        .coerceIn(-cardHeightPx * 1.4f, 0f)
                                    coroutineScope.launch {
                                        dismissOffsetY.snapTo(nextY)
                                    }
                                }
                            }
                            DragAxis.Undecided -> Unit
                        }
                    }

                    val velocity = velocityTracker.calculateVelocity()
                    val currentMaxIdx = (currentTabs.size - 1).coerceAtLeast(0)

                    if (axis == DragAxis.Vertical) {
                        val currentY = dismissOffsetY.value
                        val shouldDismiss = completed &&
                                (currentY < -dismissThresholdPx || velocity.y < -850f)

                        if (shouldDismiss) {
                            val targetIdx = scrollPosition.value.roundToInt().coerceIn(0, currentMaxIdx)
                            val targetTab = currentTabs.getOrNull(targetIdx)
                            if (targetTab != null) {
                                closingTabId = targetTab.id
                                closedFromIndex = targetIdx
                                coroutineScope.launch {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dismissOffsetY.animateTo(
                                        targetValue = -cardHeightPx * 1.15f,
                                        animationSpec = DismissExitSpec
                                    )
                                    val newMaxIdx = (currentTabs.size - 2).coerceAtLeast(0)
                                    val nextTargetIdx = targetIdx.coerceIn(0, newMaxIdx)
                                    lastHapticIndex = nextTargetIdx
                                    closingTabId = null
                                    closedFromIndex = -1
                                    scrollPosition.snapTo(nextTargetIdx.toFloat())
                                    dismissOffsetY.snapTo(0f)
                                    currentOnCloseTab(targetTab.id)
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                dismissOffsetY.animateTo(0f, SmoothStackSpring)
                            }
                        }
                    } else if (axis == DragAxis.Horizontal) {
                        val pos = scrollPosition.value
                        val indexVelocity = velocity.x / dragSensitivityPx
                        val dragDelta = pos - dragStartPos
                        val projected = pos + indexVelocity * 0.22f

                        val targetIdx = when {
                            abs(indexVelocity) > 0.85f -> projected.roundToInt()
                            dragDelta > 0.16f -> ceil(pos).toInt()
                            dragDelta < -0.16f -> floor(pos).toInt()
                            else -> pos.roundToInt()
                        }.coerceIn(0, currentMaxIdx)

                        coroutineScope.launch {
                            scrollPosition.animateTo(
                                targetValue = targetIdx.toFloat(),
                                initialVelocity = indexVelocity.coerceIn(-4.5f, 4.5f) * 0.65f,
                                animationSpec = SmoothStackSpring
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val activeClosingId = closingTabId
        val activeShiftDelta = postCloseShiftDelta.value
        val pivotIdx = if (closedFromIndex >= 0) closedFromIndex else nearestIndex

        allTabsInOrder.forEachIndexed { index, tab ->
            val shiftOffset = if (activeShiftDelta > 0f && index >= nearestIndex) {
                activeShiftDelta
            } else {
                0f
            }
            val delta = (index - currentPos) + shiftOffset
            if (delta < -1.45f || delta > 4.6f) return@forEachIndexed

            val isDismissingCard = tab.id == activeClosingId
            val isCenteredCard = if (activeClosingId != null) {
                tab.id == activeClosingId
            } else {
                index == nearestIndex
            }

            // Promote remaining cards smoothly to their post-close slots as the card exits upward
            val upwardDismissProgress = (abs(dismissOffsetY.value) / (cardHeightPx * 0.90f)).coerceIn(0f, 1f)
            val effectiveDelta = when {
                isDismissingCard -> delta
                pivotIdx < maxIndex && index > pivotIdx -> delta - upwardDismissProgress
                pivotIdx == maxIndex && index == pivotIdx - 1 -> delta + upwardDismissProgress
                else -> delta
            }

            val focusProgress = (1f - abs(effectiveDelta)).coerceIn(0f, 1f)

            val horizontalOffsetPx = if (effectiveDelta <= 0f) {
                animatedBiasPx + (cardWidthPx + cardGapPx) * (-effectiveDelta)
            } else {
                animatedBiasPx - fanSpreadPx * (1f - exp(-0.56f * effectiveDelta))
            }

            val cardScale = if (effectiveDelta <= 0f) {
                1f + (0.012f * focusProgress)
            } else {
                (1f + 0.012f * focusProgress) - 0.22f * (1f - exp(-0.46f * effectiveDelta))
            }

            val tiltAngleY = (effectiveDelta.coerceIn(-1.5f, 2.5f) * 3.2f)

            val dimAlpha = if (effectiveDelta <= 0f) {
                0f
            } else {
                (0.26f * (1f - exp(-0.48f * effectiveDelta))).coerceIn(0f, 0.28f)
            }

            val edgeAlpha = when {
                effectiveDelta < -1.05f -> (1f - ((-effectiveDelta - 1.05f) / 0.40f)).coerceIn(0f, 1f)
                effectiveDelta > 3.7f -> (1f - ((effectiveDelta - 3.7f) / 0.9f)).coerceIn(0f, 1f)
                else -> 1f
            }

            val verticalDismissPx = if (isCenteredCard) dismissOffsetY.value else 0f
            val cardDismissRatio = (abs(verticalDismissPx) / (cardHeightPx * 0.85f)).coerceIn(0f, 1f)
            val effectiveScale = cardScale * (1f - cardDismissRatio * 0.08f)
            val effectiveAlpha = edgeAlpha * (1f - cardDismissRatio * 0.65f)

            val tabStackTitle = if (!isDismissingCard && index == nearestIndex) activeStack?.title else null

            Column(
                modifier = Modifier
                    .zIndex(if (isDismissingCard) 200f else 100f - effectiveDelta)
                    .graphicsLayer {
                        translationX = horizontalOffsetPx
                        translationY = verticalDismissPx
                        scaleX = effectiveScale
                        scaleY = effectiveScale
                        rotationY = tiltAngleY
                        cameraDistance = 16f * density.density
                        alpha = effectiveAlpha
                    },
                horizontalAlignment = Alignment.Start
            ) {
                ActiveTabDomainChip(
                    domain = resolveTabChipLabel(tab),
                    stackTitle = tabStackTitle,
                    isIncognito = tab.isIncognito,
                    maxChipWidth = (cardWidth * 0.86f).coerceAtLeast(144.dp),
                    onClick = {
                        if (isDismissingCard) return@ActiveTabDomainChip
                        if (index == nearestIndex) {
                            currentOnOpenFocusedTab(tab.id)
                        } else {
                            coroutineScope.launch {
                                scrollPosition.animateTo(index.toFloat(), SmoothStackSpring)
                            }
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        alpha = focusProgress
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                IosStackedTabCard(
                    tab = tab,
                    thumbnail = thumbnailProvider(tab.id),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    focusProgress = focusProgress,
                    dimOverlayAlpha = dimAlpha,
                    onClick = {
                        if (isDismissingCard) return@IosStackedTabCard
                        if (index == nearestIndex) {
                            currentOnOpenFocusedTab(tab.id)
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                scrollPosition.animateTo(index.toFloat(), SmoothStackSpring)
                            }
                        }
                    }
                )
            }
        }
    }
}