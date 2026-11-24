package com.verve.browser.presentation.browser.components.searchpill.pageactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verve.browser.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PageActionsMiddleSections(
    isTranslated: Boolean,
    translateLanguage: String,
    onTranslateToggle: (Boolean) -> Unit,
    onTranslateSettingsClick: () -> Unit,
    onSiteControlsClick: () -> Unit,
    onFindOnPageClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onZoomClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLanguage: String = "English"
) {
    val segmentedItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )

    val isSameAsSource = translateLanguage.equals(sourceLanguage, ignoreCase = true)
    val effectiveTranslated = isTranslated && !isSameAsSource

    val actionItems = listOf(
        Triple(painterResource(id = R.drawable.ic_siteinfo), "Site controls", onSiteControlsClick),
        Triple(rememberVectorPainter(PageActionsSymbols.FindInPage), "Find on page", onFindOnPageClick),
        Triple(rememberVectorPainter(PageActionsSymbols.Download), "Downloads", onDownloadsClick),
        Triple(rememberVectorPainter(PageActionsSymbols.ZoomIn), "Zoom", onZoomClick)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Full-width Translate Card (tap card for language settings, switch to toggle)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onTranslateSettingsClick),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Translate",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (effectiveTranslated) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = sourceLanguage,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = PageActionsSymbols.SwapHoriz,
                                contentDescription = "Translation direction",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = translateLanguage,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "Off",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Switch(
                    checked = effectiveTranslated,
                    onCheckedChange = { enabled ->
                        if (enabled && isSameAsSource) {
                            onTranslateSettingsClick()
                        } else {
                            onTranslateToggle(enabled)
                        }
                    }
                )
            }
        }

        // Compact Material 3 Expressive Segmented List
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 46.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                actionItems.forEachIndexed { index, (painter, label, onClick) ->
                    SegmentedSheetActionItem(
                        index = index,
                        count = actionItems.size,
                        painter = painter,
                        label = label,
                        colors = segmentedItemColors,
                        onClick = onClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SegmentedSheetActionItem(
    index: Int,
    count: Int,
    painter: Painter,
    label: String,
    colors: ListItemColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        leadingContent = {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        },
        content = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}