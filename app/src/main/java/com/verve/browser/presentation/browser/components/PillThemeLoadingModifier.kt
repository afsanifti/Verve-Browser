package com.verve.browser.presentation.browser.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay

private const val MIN_GRADIENT_DISPLAY_MS = 650L

fun Modifier.pillTertiaryLoadingGradient(
    isLoading: Boolean,
    onAnimationCompleted: () -> Unit
): Modifier = composed {
    val colorScheme = MaterialTheme.colorScheme
    val latestOnCompleted by rememberUpdatedState(onAnimationCompleted)

    var visualLoading by remember { mutableStateOf(isLoading) }
    var animationStartMs by remember { mutableLongStateOf(0L) }
    var awaitingCommit by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            if (!visualLoading) {
                animationStartMs = SystemClock.uptimeMillis()
            }
            visualLoading = true
            awaitingCommit = true
        } else if (visualLoading) {
            val elapsed = SystemClock.uptimeMillis() - animationStartMs
            val remaining = (MIN_GRADIENT_DISPLAY_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) {
                delay(remaining)
            }
            visualLoading = false
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (visualLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visualLoading) 220 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "pillTertiaryGradientAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pillTertiaryGradientSweep")
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillTertiaryGradientPhase"
    )

    LaunchedEffect(visualLoading, overlayAlpha) {
        if (!visualLoading && awaitingCommit && overlayAlpha <= 0.01f) {
            awaitingCommit = false
            latestOnCompleted()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (awaitingCommit) {
                awaitingCommit = false
                latestOnCompleted()
            }
        }
    }

    if (overlayAlpha <= 0.005f) {
        this
    } else {
        val secondaryStop = lerp(colorScheme.secondaryContainer, colorScheme.secondary, 0.24f)
        val primaryStop = colorScheme.primaryContainer
        val tertiaryStop = lerp(colorScheme.tertiaryContainer, colorScheme.tertiary, 0.28f)

        this.drawWithContent {
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                val travel = sweepPhase * width * 2f
                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to secondaryStop,
                        0.50f to primaryStop,
                        1.00f to tertiaryStop
                    ),
                    start = Offset(-width + travel, 0f),
                    end = Offset(travel, height),
                    tileMode = TileMode.Mirror
                )
                drawRect(
                    brush = brush,
                    alpha = overlayAlpha
                )
            }
            drawContent()
        }
    }
}