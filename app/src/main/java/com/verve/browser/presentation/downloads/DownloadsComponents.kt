package com.verve.browser.presentation.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DownloadsTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    hasCompletedDownloads: Boolean,
    onNavigateBack: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearCompletedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isOverflowExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f),
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f),
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearchActive) {
                        onSearchQueryChange("")
                        onSearchActiveChange(false)
                    } else {
                        onNavigateBack()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (isSearchActive) "Close search" else "Navigate back"
                )
            }
        },
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search downloads",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                )
            } else {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search query"
                        )
                    }
                }
            } else {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search downloads"
                    )
                }

                Box {
                    IconButton(onClick = { isOverflowExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = isOverflowExpanded,
                        onDismissRequest = { isOverflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear completed") },
                            enabled = hasCompletedDownloads,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isOverflowExpanded = false
                                onClearCompletedClick()
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun DownloadsFilterRow(
    selectedFilter: DownloadFilter,
    onFilterSelected: (DownloadFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DownloadFilter.entries.forEach { filter ->
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = if (isSelected) {
                    null
                } else {
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    null
                },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            )
        }
    }
}

@Composable
internal fun ActiveDownloadCard(
    item: DownloadItem,
    onTogglePauseResume: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val animatedProgress by animateFloatAsState(
        targetValue = item.progress,
        label = "activeDownloadProgress"
    )

    val statusHeader = if (item.status == DownloadStatus.PAUSED) {
        "Paused · ${item.progressPercent}%"
    } else {
        "Downloading · ${item.progressPercent}%"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(44.dp),
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.22f),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusHeader,
                    color = contentColor.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.fileName,
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatActiveProgressSubtitle(item),
                    color = contentColor.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onCancelClick) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel download",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            Surface(
                onClick = onTogglePauseResume,
                modifier = Modifier.size(width = 48.dp, height = 44.dp),
                shape = RoundedCornerShape(16.dp),
                color = contentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.status == DownloadStatus.PAUSED) {
                            Icons.Rounded.PlayArrow
                        } else {
                            Icons.Rounded.Pause
                        },
                        contentDescription = if (item.status == DownloadStatus.PAUSED) {
                            "Resume download"
                        } else {
                            "Pause download"
                        },
                        tint = containerColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadHistoryGroupSection(
    group: DownloadDateGroup,
    onItemClick: (DownloadItem) -> Unit,
    onShareItem: (DownloadItem) -> Unit,
    onCopyLink: (DownloadItem) -> Unit,
    onDeleteItem: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = group.title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            group.items.forEachIndexed { index, item ->
                DownloadHistorySegmentedRow(
                    item = item,
                    index = index,
                    count = group.items.size,
                    colors = itemColors,
                    onClick = { onItemClick(item) },
                    onShareClick = { onShareItem(item) },
                    onCopyLinkClick = { onCopyLink(item) },
                    onDeleteClick = { onDeleteItem(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadHistorySegmentedRow(
    item: DownloadItem,
    index: Int,
    count: Int,
    colors: androidx.compose.material3.ListItemColors,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyLinkClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = colors,
        contentPadding = PaddingValues(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                imageVector = fileTypeLeadingIcon(item.fileType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        content = {
            Text(
                text = item.fileName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${formatFileSize(item.totalBytes)} · ${item.domain}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { isMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Download options for ${item.fileName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        },
                        onClick = {
                            isMenuOpen = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                        },
                        onClick = {
                            isMenuOpen = false
                            onShareClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Link, contentDescription = null)
                        },
                        onClick = {
                            isMenuOpen = false
                            onCopyLinkClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        },
                        onClick = {
                            isMenuOpen = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    )
}

private fun fileTypeLeadingIcon(fileType: DownloadFileType): ImageVector = when (fileType) {
    DownloadFileType.ARCHIVE -> Icons.Rounded.FolderZip
    else -> Icons.Rounded.Download
}