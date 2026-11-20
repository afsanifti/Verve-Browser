package com.verve.browser.presentation.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verve.browser.presentation.update.AppUpdateUiState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AboutVerveScreen(
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onUpdateClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoStartUpdate: Boolean = false,
    onAutoStartUpdateHandled: () -> Unit = {}
) {
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(Unit) {
        if (autoStartUpdate) {
            onAutoStartUpdateHandled()
            if (!updateState.isDownloading) {
                onUpdateClick()
            }
        } else if (!updateState.isDownloading && !updateState.isChecking) {
            onCheckForUpdates()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = updateState.downloadProgress.coerceIn(0f, 1f),
        label = "aboutVerveDownloadProgress"
    )

    val availableUpdate = updateState.availableUpdate
    val recentNotes = availableUpdate?.releaseNotes?.takeIf { it.isNotBlank() }
        ?: updateState.recentReleaseNotes.orEmpty()
    val recentNotesVersion = availableUpdate?.versionName
        ?: updateState.recentReleaseVersionName

    val statusTitle = when {
        updateState.isDownloading -> "Downloading update…"
        availableUpdate != null && updateState.readyApkFile != null -> "Update ready to install"
        availableUpdate != null -> "Verve update available"
        updateState.isChecking -> "Checking for updates…"
        !updateState.errorMessage.isNullOrBlank() -> "Update check failed"
        else -> "Verve is up to date"
    }

    val statusSubtitle = when {
        updateState.isDownloading -> when {
            updateState.downloadedBytes <= 0L -> "Connecting to GitHub…"
            updateState.totalBytes > 0L ->
                "${formatBytesMb(updateState.downloadedBytes)} / ${formatBytesMb(updateState.totalBytes)} (${(updateState.downloadProgress * 100).toInt()}%)"
            else ->
                "Downloaded ${formatBytesMb(updateState.downloadedBytes)}"
        }
        availableUpdate != null && updateState.readyApkFile != null ->
            "APK downloaded (${formatBytesMb(updateState.readyApkFile.length())}) and SHA-256 verified"
        availableUpdate != null ->
            "Version ${availableUpdate.versionName} is ready to download"
        updateState.isChecking ->
            "Checking GitHub releases…"
        !updateState.errorMessage.isNullOrBlank() ->
            updateState.errorMessage
        else ->
            "You’re running the latest version of Verve Browser"
    }

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
                            imageVector = SettingsSymbols.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when {
                    updateState.isDownloading -> {
                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Cancel download",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    availableUpdate != null -> {
                        Button(
                            onClick = onUpdateClick,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = if (updateState.readyApkFile != null) {
                                    "Install Verve"
                                } else {
                                    "Update Verve"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    !updateState.errorMessage.isNullOrBlank() -> {
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    else -> {
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = !updateState.isChecking,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (updateState.isChecking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Checking…",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            } else {
                                Text(
                                    text = "Check for updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp
                )
        ) {
            // Fixed Update & Version Hero Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    availableUpdate != null || updateState.isDownloading -> SettingsSymbols.SystemUpdate
                                    !updateState.errorMessage.isNullOrBlank() -> SettingsSymbols.Public
                                    else -> SettingsSymbols.CheckCircle
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verve Browser",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Version ${updateState.currentVersionName} (${updateState.currentVersionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!updateState.errorMessage.isNullOrBlank() &&
                            !updateState.isDownloading &&
                            !updateState.isChecking &&
                            availableUpdate == null
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Expressive Wavy Progress Line
                    val showIndeterminateWave = updateState.isChecking ||
                            (updateState.isDownloading && (updateState.downloadedBytes <= 0L || updateState.totalBytes <= 0L))
                    val resolvedProgress = when {
                        showIndeterminateWave -> null
                        updateState.isDownloading -> animatedProgress
                        availableUpdate != null && updateState.readyApkFile != null -> 1f
                        availableUpdate != null -> 0.14f
                        else -> 1f
                    }

                    ExpressiveWavyProgressIndicator(
                        progress = resolvedProgress,
                        isActive = updateState.isDownloading || updateState.isChecking,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!updateState.errorMessage.isNullOrBlank() && availableUpdate != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = updateState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Internally Scrollable Release Notes Container Above Fixed Update Button
            if (recentNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FormattedReleaseNotes(notes = recentNotes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveWavyProgressIndicator(
    progress: Float?,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgressTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyPhase"
    )
    val indeterminateSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminateSweep"
    )

    Canvas(
        modifier = modifier
            .height(14.dp)
            .padding(horizontal = 2.dp)
    ) {
        val centerY = size.height / 2f
        val width = size.width
        if (width <= 0f) return@Canvas

        val strokePx = 3.dp.toPx()
        val waveAmplitude = if (isActive) 2.8.dp.toPx() else 2.dp.toPx()
        val waveLength = 24.dp.toPx()
        val activePhase = if (isActive) phase else 0f

        val startX: Float
        val endX: Float
        if (progress == null) {
            val segmentWidth = width * 0.34f
            val travel = width + segmentWidth
            endX = (indeterminateSweep * travel).coerceIn(0f, width)
            startX = (indeterminateSweep * travel - segmentWidth).coerceIn(0f, width)
        } else {
            startX = 0f
            endX = width * progress.coerceIn(0f, 1f)
        }

        if (startX > 0f) {
            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(startX, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        if (endX < width) {
            drawLine(
                color = trackColor,
                start = Offset(endX, centerY),
                end = Offset(width, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }

        if (endX - startX > 2f) {
            val path = Path()
            var x = startX
            val step = 2.dp.toPx()
            var first = true
            while (x <= endX) {
                val angle = ((x / waveLength) * 2f * PI).toFloat() - activePhase
                val y = centerY + sin(angle) * waveAmplitude
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            val endAngle = ((endX / waveLength) * 2f * PI).toFloat() - activePhase
            path.lineTo(endX, centerY + sin(endAngle) * waveAmplitude)

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        drawCircle(
            color = primaryColor,
            radius = 2.2.dp.toPx(),
            center = Offset(width, centerY)
        )
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
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest

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
                            top = if (index == 0) 2.dp else 14.dp,
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
                                top = 3.dp,
                                bottom = 3.dp
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
                            text = formatInlineMarkdown(block.text, codeBackground),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
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
                        text = formatInlineMarkdown(block.text, codeBackground),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val redundantTitleRegex = Regex(
        "^(verve browser\\s*[—–-]\\s*release notes|what'?s new)$",
        RegexOption.IGNORE_CASE
    )

    return rawNotes
        .lines()
        .mapNotNull { rawLine ->
            val trimmedEnd = rawLine.trimEnd()
            val trimmed = trimmedEnd.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            headingRegex.find(trimmed)?.let { match ->
                val headingText = match.groupValues[1].trim()
                val strippedEmoji = headingText.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()
                if (redundantTitleRegex.matches(strippedEmoji)) return@mapNotNull null
                return@mapNotNull ReleaseNoteBlock.Heading(headingText)
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

private fun formatInlineMarkdown(text: String, codeBackground: Color): AnnotatedString {
    val tokenRegex = Regex("(\\*\\*(.+?)\\*\\*|`([^`]+)`)")
    return buildAnnotatedString {
        var currentIndex = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }
            val boldText = match.groups[2]?.value
            val codeText = match.groups[3]?.value
            when {
                boldText != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(boldText)
                    }
                }
                codeText != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = codeBackground
                        )
                    ) {
                        append(" $codeText ")
                    }
                }
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