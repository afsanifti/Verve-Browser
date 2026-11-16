package com.verve.browser.presentation.browser.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.TabStack
import com.verve.browser.presentation.theme.VerveBrowserTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val MaxVisibleBackgroundCards = 3
private const val ReferenceDeviceWidthDp = 412f
private const val ReferenceCardWidthDp = 269f
private const val ReferenceCardHeightDp = 564f
private const val ReferenceCardAspectRatio = ReferenceCardWidthDp / ReferenceCardHeightDp

private val StackCardSpring = spring<Float>(
    dampingRatio = 0.80f,
    stiffness = 420f
)

private val StackCardDpSpring = spring<Dp>(
    dampingRatio = 0.80f,
    stiffness = 420f
)

@Composable
fun QuickTabsScreen(
    tabs: List<BrowserTab>,
    tabStacks: List<TabStack>,
    activeTabId: String,
    thumbnailProvider: (String) -> ImageBitmap?,
    onBackClick: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onCreateTabStack: (List<String>, String) -> Unit = { _, _ -> },
    onAddTabToStack: (String, String) -> Unit = { _, _ -> },
    onRemoveTabFromStack: (String) -> Unit = {},
    onToggleStackCollapsed: (String) -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onLayoutToggleClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Always use the system wallpaper/theme engine rather than the active webpage's seed color
    VerveBrowserTheme(siteSeedColor = null) {
        var isStackSheetOpen by remember { mutableStateOf(false) }
        var focusedTabId by remember { mutableStateOf(activeTabId) }

        LaunchedEffect(tabs, activeTabId) {
            if (tabs.none { it.id == focusedTabId }) {
                focusedTabId = tabs.firstOrNull { it.id == activeTabId }?.id
                    ?: tabs.firstOrNull()?.id
                            ?: activeTabId
            }
        }

        val orderedTabs = remember(tabs, focusedTabId) {
            if (tabs.isEmpty()) {
                emptyList()
            } else {
                val focusIndex = tabs.indexOfFirst { it.id == focusedTabId }.coerceAtLeast(0)
                tabs.drop(focusIndex) + tabs.take(focusIndex)
            }
        }

        val focusedTab = orderedTabs.firstOrNull()
        val focusedStack = remember(focusedTab, tabStacks) {
            focusedTab?.stackId?.let { stackId -> tabStacks.firstOrNull { it.id == stackId } }
        }

        val topSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        val bottomNavInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(topSafeInsets)
            ) {
                QuickTabsTopBar(
                    stackCount = tabStacks.size,
                    onBackClick = onBackClick,
                    onHistoryClick = onHistoryClick,
                    onLayoutToggleClick = onLayoutToggleClick
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isLandscape = maxWidth > maxHeight
                    val chipAndGapHeight = 46.dp
                    val availableCardHeight = (maxHeight - chipAndGapHeight).coerceAtLeast(160.dp)

                    val cardWidth: Dp
                    val cardHeight: Dp
                    if (isLandscape) {
                        cardHeight = (availableCardHeight * 0.92f).coerceIn(150.dp, 310.dp)
                        cardWidth = (cardHeight * 1.15f).coerceIn(180.dp, (maxWidth * 0.52f).coerceAtLeast(180.dp))
                    } else {
                        val fullScreenWidth = maxWidth + 32.dp
                        val proportionalWidth = fullScreenWidth * (ReferenceCardWidthDp / ReferenceDeviceWidthDp)
                        val proportionalHeight = proportionalWidth / ReferenceCardAspectRatio
                        val maxAllowedHeight = (availableCardHeight * 0.89f).coerceAtLeast(220.dp)

                        cardHeight = proportionalHeight.coerceAtMost(maxAllowedHeight)
                        cardWidth = (cardHeight * ReferenceCardAspectRatio).coerceAtLeast(160.dp)
                    }

                    QuickTabsCardStack(
                        tabs = orderedTabs,
                        allTabsInOrder = tabs,
                        activeStack = focusedStack,
                        thumbnailProvider = thumbnailProvider,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onFocusTab = { targetId -> focusedTabId = targetId },
                        onOpenFocusedTab = { tabId ->
                            if (tabId == activeTabId) {
                                onBackClick()
                            } else {
                                onSelectTab(tabId)
                            }
                        },
                        onCloseTab = { tabId ->
                            if (tabId == focusedTabId && tabs.size > 1) {
                                val nextFocus = orderedTabs.getOrNull(1)?.id
                                if (nextFocus != null) {
                                    focusedTabId = nextFocus
                                }
                            }
                            onCloseTab(tabId)
                        }
                    )
                }

                QuickTabsBottomBar(
                    hasStacks = tabStacks.isNotEmpty(),
                    onLayersClick = { isStackSheetOpen = true },
                    onNewTabClick = onNewTabClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.windowInsetsPadding(bottomNavInsets)
                )
            }

            if (isStackSheetOpen && focusedTab != null) {
                TabStackManagementDialog(
                    activeTab = focusedTab,
                    allTabs = tabs,
                    tabStacks = tabStacks,
                    onDismiss = { isStackSheetOpen = false },
                    onCreateTabStack = { tabIds, title ->
                        onCreateTabStack(tabIds, title)
                        isStackSheetOpen = false
                    },
                    onAddTabToStack = { tabId, stackId ->
                        onAddTabToStack(tabId, stackId)
                        isStackSheetOpen = false
                    },
                    onRemoveTabFromStack = { tabId ->
                        onRemoveTabFromStack(tabId)
                        isStackSheetOpen = false
                    },
                    onToggleStackCollapsed = { stackId ->
                        onToggleStackCollapsed(stackId)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickTabsTopBar(
    stackCount: Int,
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLayoutToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to browser",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = if (stackCount > 0) "Quick Tabs · $stackCount stacks" else "Quick Tabs",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.W500
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onHistoryClick) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = "History",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onLayoutToggleClick) {
            Icon(
                imageVector = Icons.Rounded.ViewCarousel,
                contentDescription = "Tab layout",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QuickTabsCardStack(
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
    val activeTab = tabs.firstOrNull() ?: return
    val backgroundTabs = tabs.drop(1).take(MaxVisibleBackgroundCards)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val cardWidthPx = with(density) { cardWidth.toPx() }.coerceAtLeast(1f)
    val dragOffsetX = remember { Animatable(0f) }

    val dragProgress = (abs(dragOffsetX.value) / (cardWidthPx * 0.65f)).coerceIn(0f, 1f)

    val foregroundHorizontalOffset = when (backgroundTabs.size) {
        0 -> 0.dp
        1 -> 14.dp
        2 -> 20.dp
        else -> 24.dp
    }
    val chipReserveHeight = 46.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(allTabsInOrder, activeTab.id) {
                if (allTabsInOrder.size <= 1) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        coroutineScope.launch { dragOffsetX.stop() }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val clampedTarget = (dragOffsetX.value + dragAmount)
                                .coerceIn(-cardWidthPx * 0.95f, cardWidthPx * 0.95f)
                            dragOffsetX.snapTo(clampedTarget)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffsetX.animateTo(0f, StackCardSpring)
                        }
                    },
                    onDragEnd = {
                        val currentOffset = dragOffsetX.value
                        val currentIndex = allTabsInOrder.indexOfFirst { it.id == activeTab.id }
                            .coerceAtLeast(0)

                        if (abs(currentOffset) >= swipeThresholdPx && allTabsInOrder.size > 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val nextIndex = if (currentOffset > 0f) {
                                // Swipe right peels top card to reveal next tab in stack
                                (currentIndex + 1) % allTabsInOrder.size
                            } else {
                                // Swipe left brings previous tab in stack onto the front
                                (currentIndex - 1 + allTabsInOrder.size) % allTabsInOrder.size
                            }
                            onFocusTab(allTabsInOrder[nextIndex].id)
                        }

                        coroutineScope.launch {
                            dragOffsetX.animateTo(0f, StackCardSpring)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        for (stackIndex in backgroundTabs.indices.reversed()) {
            val depth = stackIndex + 1
            val bgTab = backgroundTabs[stackIndex]

            // As user drags the top card, promote the background cards smoothly toward the front
            val effectiveDepth = (depth - dragProgress).coerceAtLeast(0f)
            val targetScale = (1f - effectiveDepth * 0.055f).coerceAtLeast(0.80f)
            val targetHorizontalStep = foregroundHorizontalOffset - (24.dp * effectiveDepth)
            val targetVerticalStep = (chipReserveHeight / 2f) + (effectiveDepth * 4.dp)
            val targetDimAlpha = (effectiveDepth * 0.10f).coerceAtMost(0.30f)

            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = StackCardSpring,
                label = "bgCardScale_$depth"
            )
            val animatedOffsetX by animateDpAsState(
                targetValue = targetHorizontalStep,
                animationSpec = StackCardDpSpring,
                label = "bgCardOffsetX_$depth"
            )
            val animatedOffsetY by animateDpAsState(
                targetValue = targetVerticalStep,
                animationSpec = StackCardDpSpring,
                label = "bgCardOffsetY_$depth"
            )

            BackgroundStackedTabCard(
                tab = bgTab,
                thumbnail = thumbnailProvider(bgTab.id),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                dimOverlayAlpha = targetDimAlpha,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFocusTab(bgTab.id)
                },
                modifier = Modifier
                    .zIndex((MaxVisibleBackgroundCards - depth).toFloat())
                    .offset(x = animatedOffsetX, y = animatedOffsetY)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
            )
        }

        val animatedForegroundBaseX by animateDpAsState(
            targetValue = foregroundHorizontalOffset,
            animationSpec = StackCardDpSpring,
            label = "fgCardBaseX"
        )

        Column(
            modifier = Modifier
                .zIndex((MaxVisibleBackgroundCards + 1).toFloat())
                .offset(x = animatedForegroundBaseX)
                .graphicsLayer {
                    translationX = dragOffsetX.value
                    rotationZ = (dragOffsetX.value / cardWidthPx) * 7f
                },
            horizontalAlignment = Alignment.Start
        ) {
            ActiveTabDomainChip(
                domain = resolveTabChipLabel(activeTab),
                stackTitle = activeStack?.title,
                isIncognito = activeTab.isIncognito,
                maxChipWidth = (cardWidth * 0.86f).coerceAtLeast(144.dp),
                onClick = { onOpenFocusedTab(activeTab.id) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ForegroundTabCard(
                tab = activeTab,
                thumbnail = thumbnailProvider(activeTab.id),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                onClick = { onOpenFocusedTab(activeTab.id) },
                onCloseClick = { onCloseTab(activeTab.id) }
            )
        }
    }
}

@Composable
private fun ActiveTabDomainChip(
    domain: String,
    stackTitle: String?,
    isIncognito: Boolean,
    maxChipWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val label = buildString {
        if (!stackTitle.isNullOrBlank()) {
            append(stackTitle)
            append(" · ")
        } else if (isIncognito) {
            append("Incognito · ")
        }
        append(domain)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 128.dp, max = maxChipWidth)
            .height(34.dp),
        shape = CircleShape,
        color = chipColor,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Active tab",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ForegroundTabCard(
    tab: BrowserTab,
    thumbnail: ImageBitmap?,
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardSurfaceColor = resolveCardSurfaceColor(tab)

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        color = cardSurfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabCardThumbnailContent(
                tab = tab,
                thumbnail = thumbnail,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close tab",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundStackedTabCard(
    tab: BrowserTab,
    thumbnail: ImageBitmap?,
    cardWidth: Dp,
    cardHeight: Dp,
    dimOverlayAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardSurfaceColor = resolveCardSurfaceColor(tab)

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        color = cardSurfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabCardThumbnailContent(
                tab = tab,
                thumbnail = thumbnail,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = dimOverlayAlpha))
            )
        }
    }
}

@Composable
private fun TabCardThumbnailContent(
    tab: BrowserTab,
    thumbnail: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail,
            contentDescription = tab.title.ifBlank { resolveTabChipLabel(tab) },
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier
        )
        return
    }

    if (tab.url.isBlank()) {
        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (tab.isIncognito) "Incognito Tab" else "New Tab",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Search or enter address",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val surfaceColor = resolveCardSurfaceColor(tab)
        val isDarkSurface = surfaceColor.luminance() < 0.4f
        val primaryText = if (isDarkSurface) Color(0xFFE6E8EE) else Color(0xFF1C1B1F)
        val secondaryText = if (isDarkSurface) Color(0xFF9AA0AC) else Color(0xFF5F6368)

        Column(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (tab.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = tab.title.ifBlank { resolveTabChipLabel(tab) },
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = resolveTabChipLabel(tab),
                color = secondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun resolveTabChipLabel(tab: BrowserTab): String {
    if (tab.url.isBlank()) return if (tab.isIncognito) "Incognito" else "New Tab"
    return extractDisplayDomain(tab.url).ifBlank { tab.title.ifBlank { "Webpage" } }
}

@Composable
private fun resolveCardSurfaceColor(tab: BrowserTab): Color {
    if (tab.url.isBlank()) return MaterialTheme.colorScheme.surfaceContainerHigh
    return tab.topRegionColor ?: tab.siteSeedColor ?: MaterialTheme.colorScheme.surfaceContainerLowest
}

@Composable
private fun QuickTabsBottomBar(
    hasStacks: Boolean,
    onLayersClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onLayersClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = "Tab stacks",
                tint = if (hasStacks) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
        }

        Surface(
            onClick = onNewTabClick,
            modifier = Modifier
                .width(76.dp)
                .height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 3.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New tab",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TabStackManagementDialog(
    activeTab: BrowserTab,
    allTabs: List<BrowserTab>,
    tabStacks: List<TabStack>,
    onDismiss: () -> Unit,
    onCreateTabStack: (List<String>, String) -> Unit,
    onAddTabToStack: (String, String) -> Unit,
    onRemoveTabFromStack: (String) -> Unit,
    onToggleStackCollapsed: (String) -> Unit
) {
    val ungroupedNormalTabs = remember(allTabs) {
        allTabs.filter { !it.isIncognito && it.stackId == null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tab Stacks",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeTab.stackId != null) {
                    Text(
                        text = "Current tab is in a stack.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onRemoveTabFromStack(activeTab.id) }) {
                        Text("Remove current tab from stack")
                    }
                } else if (!activeTab.isIncognito && tabStacks.isNotEmpty()) {
                    Text(
                        text = "Add current tab to an existing stack:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    tabStacks.forEach { stack ->
                        TextButton(onClick = { onAddTabToStack(activeTab.id, stack.id) }) {
                            Text("Add to \"${stack.title}\"")
                        }
                    }
                }

                if (ungroupedNormalTabs.size >= 2) {
                    TextButton(
                        onClick = {
                            val title = resolveTabChipLabel(ungroupedNormalTabs.first())
                            onCreateTabStack(ungroupedNormalTabs.map { it.id }, title)
                        }
                    ) {
                        Text("Stack ${ungroupedNormalTabs.size} open tabs")
                    }
                }

                if (tabStacks.isNotEmpty()) {
                    tabStacks.forEach { stack ->
                        val count = allTabs.count { it.stackId == stack.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleStackCollapsed(stack.id) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stack.title} ($count tabs)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (stack.isCollapsed) "Expand" else "Collapse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (ungroupedNormalTabs.size < 2) {
                    Text(
                        text = "Open at least 2 normal tabs to create a stack. Incognito tabs are never saved or stacked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}