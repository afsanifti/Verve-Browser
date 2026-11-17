package com.verve.browser.presentation.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.update.AppUpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutVerveScreen(
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onUpdateClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(Unit) {
        if (!updateState.isDownloading && !updateState.isChecking) {
            onCheckForUpdates()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = updateState.downloadProgress,
        label = "aboutVerveDownloadProgress"
    )

    val availableUpdate = updateState.availableUpdate
    val recentNotes = availableUpdate?.releaseNotes?.takeIf { it.isNotBlank() }
        ?: updateState.recentReleaseNotes.orEmpty()
    val recentNotesVersion = availableUpdate?.versionName
        ?: updateState.recentReleaseVersionName

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About Verve",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            // Current Version Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Verve Browser",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version ${updateState.currentVersionName} (${updateState.currentVersionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Update Status & Progress Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    when {
                        updateState.isChecking && availableUpdate == null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Checking for updates…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        availableUpdate != null -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = when {
                                            updateState.isDownloading -> "Downloading update…"
                                            updateState.readyApkFile != null -> "Update ready to install"
                                            else -> "Update available"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${updateState.currentVersionName} → ${availableUpdate.versionName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (updateState.isDownloading) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = when {
                                                    updateState.downloadedBytes <= 0L ->
                                                        "Connecting to GitHub…"
                                                    updateState.totalBytes > 0L ->
                                                        "Downloaded: ${formatBytesMb(updateState.downloadedBytes)} / ${formatBytesMb(updateState.totalBytes)}"
                                                    else ->
                                                        "Downloaded: ${formatBytesMb(updateState.downloadedBytes)}"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            if (updateState.downloadedBytes > 0L && updateState.totalBytes > 0L) {
                                                Text(
                                                    text = "${(updateState.downloadProgress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (updateState.downloadedBytes > 0L && updateState.totalBytes > 0L) {
                                            LinearProgressIndicator(
                                                progress = { animatedProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                            )
                                        }
                                    }
                                }
                            } else if (updateState.readyApkFile != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "APK downloaded (${formatBytesMb(updateState.readyApkFile.length())}) and SHA-256 verified.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (!updateState.errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = updateState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (updateState.isDownloading) {
                                    OutlinedButton(
                                        onClick = onCancelDownload,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                }

                                Button(
                                    onClick = onUpdateClick,
                                    enabled = !updateState.isDownloading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (updateState.isDownloading) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (updateState.downloadedBytes > 0L && updateState.totalBytes > 0L) {
                                                "Downloading ${(updateState.downloadProgress * 100).toInt()}%"
                                            } else {
                                                "Starting…"
                                            }
                                        )
                                    } else {
                                        Text(
                                            if (updateState.readyApkFile != null) {
                                                "Install Verve"
                                            } else {
                                                "Update Verve"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        !updateState.errorMessage.isNullOrBlank() -> {
                            Text(
                                text = "Update check failed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onCheckForUpdates,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry")
                            }
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Verve is up to date",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = onCheckForUpdates,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Check for updates")
                            }
                        }
                    }
                }
            }

            // Full Changelog Container with formatted headlines and bullet lists
            if (recentNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (!recentNotesVersion.isNullOrBlank()) {
                                "What's new in $recentNotesVersion"
                            } else {
                                "What's new"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FormattedReleaseNotes(notes = recentNotes)
                    }
                }
            }
        }
    }
}

private sealed interface ReleaseNoteBlock {
    data class Heading(val text: String) : ReleaseNoteBlock
    data class Bullet(val text: String, val isSubBullet: Boolean = false) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
}

@Composable
private fun FormattedReleaseNotes(
    notes: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(notes) { parseReleaseNoteBlocks(notes) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ReleaseNoteBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            top = if (index == 0) 0.dp else 14.dp,
                            bottom = 4.dp
                        )
                    )
                }

                is ReleaseNoteBlock.Bullet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (block.isSubBullet) 16.dp else 0.dp,
                                top = 2.dp,
                                bottom = 2.dp
                            ),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.isSubBullet) "◦" else "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (block.isSubBullet) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.width(16.dp)
                        )
                        Text(
                            text = parseInlineBold(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (block.isSubBullet) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ReleaseNoteBlock.Paragraph -> {
                    Text(
                        text = parseInlineBold(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun parseReleaseNoteBlocks(rawNotes: String): List<ReleaseNoteBlock> {
    val headingRegex = Regex("^#{1,3}\\s+(.+)$")
    val subBulletRegex = Regex("^\\s{2,}[-*•]\\s+(.+)$")
    val bulletRegex = Regex("^[-*•]\\s+(.+)$")

    return rawNotes
        .lines()
        .mapNotNull { rawLine ->
            val trimmedEnd = rawLine.trimEnd()
            val trimmed = trimmedEnd.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            headingRegex.find(trimmed)?.let { match ->
                return@mapNotNull ReleaseNoteBlock.Heading(match.groupValues[1].trim())
            }
            subBulletRegex.find(trimmedEnd)?.let { match ->
                return@mapNotNull ReleaseNoteBlock.Bullet(
                    text = match.groupValues[1].trim(),
                    isSubBullet = true
                )
            }
            bulletRegex.find(trimmed)?.let { match ->
                return@mapNotNull ReleaseNoteBlock.Bullet(
                    text = match.groupValues[1].trim(),
                    isSubBullet = false
                )
            }
            ReleaseNoteBlock.Paragraph(trimmed)
        }
}

private fun parseInlineBold(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        for (match in boldRegex.findAll(text)) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(match.groupValues[1])
            }
            currentIndex = match.range.last + 1
        }
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

private fun formatBytesMb(bytes: Long): String {
    val mb = bytes / (1024f * 1024f)
    return "%.1f MB".format(mb)
}