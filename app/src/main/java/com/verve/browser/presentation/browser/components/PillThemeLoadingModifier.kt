package com.verve.browser.presentation.browser.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

fun Modifier.pillTertiaryLoadingGradient(
    isLoading: Boolean,
    onAnimationCompleted: () -> Unit
): Modifier = composed {
    val colorScheme = MaterialTheme.colorScheme
    var wasLoading by remember { mutableStateOf(false) }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isLoading) 220 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "pillGradientAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pillGradientSweep")
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillGradientPhase"
    )

    LaunchedEffect(isLoading, overlayAlpha) {
        if (isLoading) {
            wasLoading = true
        } else if (wasLoading && overlayAlpha <= 0.01f) {
            wasLoading = false
            onAnimationCompleted()
        }
    }

    if (overlayAlpha <= 0.005f) {
        this
    } else {
        // Luminous Material 3 triad matching the secondary (sky) -> primary (lavender) -> tertiary (rose) sweep
        val coolAccent = colorScheme.secondaryContainer.copy(alpha = 0.95f)
        val centerAccent = colorScheme.primaryContainer.copy(alpha = 0.92f)
        val warmTertiary = colorScheme.tertiaryContainer.copy(alpha = 0.96f)

        this.drawWithContent {
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                // Smooth back-and-forth horizontal wave travel across the pill
                val waveOffset = sin(sweepPhase * 2f * PI.toFloat()) * (width * 0.28f)
                val startX = -width * 0.15f + waveOffset
                val endX = width * 1.15f + waveOffset

                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to coolAccent,
                        0.48f to centerAccent,
                        1.00f to warmTertiary
                    ),
                    start = Offset(startX, height * 0.15f),
                    end = Offset(endX, height * 0.85f)
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