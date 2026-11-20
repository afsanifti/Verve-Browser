package com.verve.browser.presentation.browser.components.quicktab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.verve.browser.R
import com.verve.browser.presentation.browser.BrowserTab
import com.verve.browser.presentation.browser.TabStack
import com.verve.browser.presentation.browser.components.searchpill.extractDisplayDomain

@Composable
internal fun ActiveTabDomainChip(
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
                imageVector = QuickTabSymbols.KeyboardArrowDown,
                contentDescription = "Active tab",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun IosStackedTabCard(
    tab: BrowserTab,
    thumbnail: ImageBitmap?,
    cardWidth: Dp,
    cardHeight: Dp,
    focusProgress: Float,
    dimOverlayAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardSurfaceColor = resolveCardSurfaceColor(tab)
    val animatedElevation = lerp(3.dp, 10.dp, focusProgress)
    val borderAlpha = 0.35f + (0.20f * focusProgress)

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(26.dp),
        color = cardSurfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha)),
        shadowElevation = animatedElevation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabCardThumbnailContent(
                tab = tab,
                thumbnail = thumbnail,
                modifier = Modifier.fillMaxSize()
            )

            if (dimOverlayAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = dimOverlayAlpha))
                )
            }
        }
    }
}

@Composable
internal fun QuickTabsGridView(
    tabs: List<BrowserTab>,
    tabStacks: List<TabStack>,
    activeTabId: String,
    thumbnailProvider: (String) -> ImageBitmap?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val stacksById = remember(tabStacks) { tabStacks.associateBy { it.id } }

    LaunchedEffect(Unit) {
        val activeIdx = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIdx > 1) {
            gridState.scrollToItem(activeIdx)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = tabs,
            key = { it.id }
        ) { tab ->
            val stack = tab.stackId?.let { stacksById[it] }
            ChromeGridTabCard(
                tab = tab,
                stackTitle = stack?.title,
                isActive = tab.id == activeTabId,
                thumbnail = thumbnailProvider(tab.id),
                onClick = { onSelectTab(tab.id) },
                onCloseClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCloseTab(tab.id)
                },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
internal fun ChromeGridTabCard(
    tab: BrowserTab,
    stackTitle: String?,
    isActive: Boolean,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frameColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val headerContentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = if (isActive) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }

    val titleLabel = remember(tab.title, tab.url, tab.isIncognito) {
        tab.title.ifBlank { resolveTabChipLabel(tab) }
    }
    val siteInitial = remember(tab.url, tab.title) {
        val domain = extractDisplayDomain(tab.url)
        val source = domain.ifBlank { tab.title }.trim()
        source.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "V"
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = frameColor,
        border = borderStroke,
        shadowElevation = if (isActive) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
        ) {
            // Compact Chrome-style card header: site badge + title + close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(start = 7.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (tab.isIncognito) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (tab.isIncognito) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_eyes_closed),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                    } else if (tab.url.isBlank()) {
                        Icon(
                            imageVector = QuickTabSymbols.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Text(
                            text = siteInitial,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (!stackTitle.isNullOrBlank()) "$stackTitle · $titleLabel" else titleLabel,
                    color = headerContentColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCloseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = QuickTabSymbols.Close,
                        contentDescription = "Close tab",
                        tint = headerContentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Concentric inner webpage preview container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(resolveCardSurfaceColor(tab))
            ) {
                TabCardThumbnailContent(
                    tab = tab,
                    thumbnail = thumbnail,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun TabCardThumbnailContent(
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
                        imageVector = QuickTabSymbols.Search,
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

internal fun resolveTabChipLabel(tab: BrowserTab): String {
    if (tab.url.isBlank()) return if (tab.isIncognito) "Incognito" else "New Tab"
    return extractDisplayDomain(tab.url).ifBlank { tab.title.ifBlank { "Webpage" } }
}

@Composable
internal fun resolveCardSurfaceColor(tab: BrowserTab): Color {
    if (tab.url.isBlank()) return MaterialTheme.colorScheme.surfaceContainerHigh
    return tab.topRegionColor ?: tab.siteSeedColor ?: MaterialTheme.colorScheme.surfaceContainerLowest
}