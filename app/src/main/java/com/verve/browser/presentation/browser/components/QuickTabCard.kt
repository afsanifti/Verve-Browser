package com.verve.browser.presentation.browser.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.verve.browser.presentation.browser.BrowserTab
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
                imageVector = Icons.Rounded.KeyboardArrowDown,
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

internal fun resolveTabChipLabel(tab: BrowserTab): String {
    if (tab.url.isBlank()) return if (tab.isIncognito) "Incognito" else "New Tab"
    return extractDisplayDomain(tab.url).ifBlank { tab.title.ifBlank { "Webpage" } }
}

@Composable
internal fun resolveCardSurfaceColor(tab: BrowserTab): Color {
    if (tab.url.isBlank()) return MaterialTheme.colorScheme.surfaceContainerHigh
    return tab.topRegionColor ?: tab.siteSeedColor ?: MaterialTheme.colorScheme.surfaceContainerLowest
}