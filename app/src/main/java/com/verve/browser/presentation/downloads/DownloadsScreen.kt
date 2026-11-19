package com.verve.browser.presentation.downloads

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.verve.browser.data.download.VerveDownloadManager
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun DownloadsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val downloads by VerveDownloadManager.downloads.collectAsState()

    DownloadsScreen(
        downloads = downloads,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onTogglePauseResume = { target ->
            VerveDownloadManager.togglePauseResume(context, target.id)
        },
        onCancelDownload = { target ->
            VerveDownloadManager.cancelDownload(target.id)
        },
        onOpenDownload = { target ->
            if (!openLocalDownloadedFile(context, target)) {
                onOpenUrl(target.url)
            }
        },
        onShareDownload = { target ->
            shareDownloadedFileOrUrl(context, target)
        },
        onDeleteDownload = { target ->
            VerveDownloadManager.deleteDownload(target.id)
        },
        onClearCompleted = {
            VerveDownloadManager.clearCompleted()
        }
    )
}

private fun openLocalDownloadedFile(context: Context, item: DownloadItem): Boolean {
    val path = item.localFilePath ?: return false
    val file = File(path)
    return file.exists() && runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
        true
    }.getOrDefault(false)
}

private fun shareDownloadedFileOrUrl(context: Context, item: DownloadItem) {
    val file = item.localFilePath?.let { File(it) }?.takeIf { it.exists() }
    val shareIntent = if (file != null) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    } else {
        null
    } ?: Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.fileName)
        putExtra(Intent.EXTRA_TEXT, item.url)
    }

    context.startActivity(
        Intent.createChooser(shareIntent, "Share ${item.fileName}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadItem>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePauseResume: (DownloadItem) -> Unit = {},
    onCancelDownload: (DownloadItem) -> Unit = {},
    onOpenDownload: (DownloadItem) -> Unit = {},
    onShareDownload: (DownloadItem) -> Unit = {},
    onDeleteDownload: (DownloadItem) -> Unit = {},
    onClearCompleted: () -> Unit = {}
) {
    var selectedFilter by rememberSaveable { mutableStateOf(DownloadFilter.ALL) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val opaqueBackground = MaterialTheme.colorScheme.background.copy(alpha = 1f)

    val filteredDownloads = remember(downloads, selectedFilter, searchQuery) {
        downloads.filter { item ->
            val matchesFilter = when (selectedFilter) {
                DownloadFilter.ALL -> true
                DownloadFilter.IN_PROGRESS -> item.isInProgress
            }
            val matchesQuery = searchQuery.isBlank() ||
                    item.fileName.contains(searchQuery, ignoreCase = true) ||
                    item.domain.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    val activeDownloads = remember(filteredDownloads) {
        filteredDownloads.filter { it.isInProgress }
    }

    val groupedHistory = remember(filteredDownloads) {
        filteredDownloads
            .filterNot { it.isInProgress }
            .groupBy { it.dateGroup }
            .map { (dateTitle, items) -> DownloadDateGroup(title = dateTitle, items = items) }
    }

    val hasCompletedDownloads = remember(downloads) {
        downloads.any { !it.isInProgress }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = opaqueBackground
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = opaqueBackground,
            topBar = {
                Column {
                    DownloadsTopBar(
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        hasCompletedDownloads = hasCompletedDownloads,
                        onNavigateBack = onNavigateBack,
                        onSearchActiveChange = { isSearchActive = it },
                        onSearchQueryChange = { searchQuery = it },
                        onClearCompletedClick = onClearCompleted
                    )

                    DownloadsFilterRow(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it }
                    )
                }
            }
        ) { innerPadding ->
            if (activeDownloads.isEmpty() && groupedHistory.isEmpty()) {
                EmptyDownloadsState(
                    selectedFilter = selectedFilter,
                    hasSearchQuery = searchQuery.isNotBlank(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(
                        items = activeDownloads,
                        key = { it.id }
                    ) { activeItem ->
                        ActiveDownloadCard(
                            item = activeItem,
                            onTogglePauseResume = { onTogglePauseResume(activeItem) },
                            onCancelClick = { onCancelDownload(activeItem) }
                        )
                    }

                    items(
                        items = groupedHistory,
                        key = { it.title }
                    ) { group ->
                        DownloadHistoryGroupSection(
                            group = group,
                            onItemClick = onOpenDownload,
                            onShareItem = onShareDownload,
                            onCopyLink = { item ->
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("download_url", item.url))
                                    )
                                }
                            },
                            onDeleteItem = onDeleteDownload
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(
    selectedFilter: DownloadFilter,
    hasSearchQuery: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when {
        hasSearchQuery -> "No matching downloads"
        selectedFilter == DownloadFilter.IN_PROGRESS -> "No active downloads"
        else -> "No downloads yet"
    }
    val subtitle = when {
        hasSearchQuery -> "Try searching with a different file name or site."
        selectedFilter == DownloadFilter.IN_PROGRESS -> "Files currently downloading will appear here."
        else -> "Files you download in Verve Browser will appear here."
    }

    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}