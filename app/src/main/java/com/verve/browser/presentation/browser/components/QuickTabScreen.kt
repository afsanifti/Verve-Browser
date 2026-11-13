package com.verve.browser.presentation.browser.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.theme.VerveBrowserTheme

private const val MaxVisibleBackgroundCards = 3
private const val ReferenceDeviceWidthDp = 412f
private const val ReferenceCardWidthDp = 269f
private const val ReferenceCardHeightDp = 564f
private const val ReferenceCardAspectRatio = ReferenceCardWidthDp / ReferenceCardHeightDp

@Composable
fun QuickTabsScreen(
    tabs: List<BrowserTab>,
    activeTabId: String,
    thumbnailProvider: (String) -> ImageBitmap?,
    onBackClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onLayoutToggleClick: () -> Unit = {},
    onTabGroupsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Always use the system wallpaper/theme engine rather than the active webpage's seed color
    VerveBrowserTheme(siteSeedColor = null) {
        val orderedTabs = remember(tabs, activeTabId) {
            val active = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
            if (active == null) {
                emptyList()
            } else {
                listOf(active) + tabs.filter { it.id != active.id }
            }
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
                        thumbnailProvider = thumbnailProvider,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onActiveCardClick = onBackClick
                    )
                }

                QuickTabsBottomBar(
                    onLayersClick = onTabGroupsClick,
                    onNewTabClick = onNewTabClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.windowInsetsPadding(bottomNavInsets)
                )
            }
        }
    }
}

@Composable
private fun QuickTabsTopBar(
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
            text = "Quick Tabs",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onHistoryClick) {
            Icon(
                imageVector = HistoryVectorIcon,
                contentDescription = "History",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onLayoutToggleClick) {
            Icon(
                imageVector = CarouselLayoutVectorIcon,
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
    thumbnailProvider: (String) -> ImageBitmap?,
    cardWidth: Dp,
    cardHeight: Dp,
    onActiveCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = tabs.firstOrNull() ?: return
    val backgroundTabs = tabs.drop(1).take(MaxVisibleBackgroundCards)

    val foregroundHorizontalOffset = when (backgroundTabs.size) {
        0 -> 0.dp
        1 -> 14.dp
        2 -> 20.dp
        else -> 24.dp
    }
    val chipReserveHeight = 46.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        for (stackIndex in backgroundTabs.indices.reversed()) {
            val depth = stackIndex + 1
            val bgTab = backgroundTabs[stackIndex]
            val scale = (1f - depth * 0.055f).coerceAtLeast(0.80f)
            val horizontalStep = foregroundHorizontalOffset - (24.dp * depth)
            val verticalStep = (chipReserveHeight / 2f) + (depth * 4.dp)
            val dimAlpha = (depth * 0.10f).coerceAtMost(0.30f)

            BackgroundStackedTabCard(
                tab = bgTab,
                thumbnail = thumbnailProvider(bgTab.id),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                dimOverlayAlpha = dimAlpha,
                modifier = Modifier
                    .zIndex((MaxVisibleBackgroundCards - depth).toFloat())
                    .offset(x = horizontalStep, y = verticalStep)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

        Column(
            modifier = Modifier
                .zIndex((MaxVisibleBackgroundCards + 1).toFloat())
                .offset(x = foregroundHorizontalOffset),
            horizontalAlignment = Alignment.Start
        ) {
            ActiveTabDomainChip(
                domain = resolveTabChipLabel(activeTab),
                maxChipWidth = (cardWidth * 0.78f).coerceAtLeast(136.dp),
                onClick = onActiveCardClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            ForegroundTabCard(
                tab = activeTab,
                thumbnail = thumbnailProvider(activeTab.id),
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                onClick = onActiveCardClick
            )
        }
    }
}

@Composable
private fun ActiveTabDomainChip(
    domain: String,
    maxChipWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

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
                text = domain,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
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
    modifier: Modifier = Modifier
) {
    val cardSurfaceColor = resolveCardSurfaceColor(tab)

    Surface(
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
                text = "New Tab",
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
    if (tab.url.isBlank()) return "New Tab"
    return extractDisplayDomain(tab.url).ifBlank { tab.title.ifBlank { "Webpage" } }
}

@Composable
private fun resolveCardSurfaceColor(tab: BrowserTab): Color {
    if (tab.url.isBlank()) return MaterialTheme.colorScheme.surfaceContainerHigh
    return tab.topRegionColor ?: tab.siteSeedColor ?: MaterialTheme.colorScheme.surfaceContainerLowest
}

@Composable
private fun QuickTabsBottomBar(
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
                imageVector = LayersVectorIcon,
                contentDescription = "Tab groups",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

private val HistoryVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z " +
                        "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

private val CarouselLayoutVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CarouselLayout",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M7,19h10L17,4L7,4v15z " +
                        "M2,17h4L6,6L2,6v11z " +
                        "M18,6v11h4L22,6h-4z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

private val LayersVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Layers",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74z " +
                        "M12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}