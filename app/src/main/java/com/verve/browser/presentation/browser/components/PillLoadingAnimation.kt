package com.verve.browser.presentation.browser.components

import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val MIN_LOADING_DISPLAY = 650.milliseconds
private const val TWO_PI = (2.0 * PI).toFloat()
private const val LOOP_PERIOD_S = 6.0f

@Stable
private class PillLoadingAnimation(
    val intensity: State<Float>,
    val time: State<Float>,
)

@Composable
private fun rememberPillLoadingAnimation(
    isLoading: Boolean,
    onAnimationCompleted: () -> Unit,
    minDisplay: Duration = MIN_LOADING_DISPLAY,
): PillLoadingAnimation {
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
            val elapsed = (SystemClock.uptimeMillis() - animationStartMs).milliseconds
            val remaining = (minDisplay - elapsed).coerceAtLeast(Duration.ZERO)
            if (remaining > Duration.ZERO) {
                delay(remaining)
            }
            visualLoading = false
        }
    }

    val intensity = animateFloatAsState(
        targetValue = if (visualLoading) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visualLoading) 300 else 460,
            easing = FastOutSlowInEasing,
        ),
        label = "pillLoadingIntensity",
    )

    val time = remember { mutableFloatStateOf(0f) }
    val visible by remember { derivedStateOf { intensity.value > 0f } }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
        }
    }

    LaunchedEffect(visualLoading, visible) {
        if (!visualLoading && awaitingCommit && !visible) {
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

    return remember(intensity, time) { PillLoadingAnimation(intensity, time) }
}

@Immutable
private class PillLoadingColors(
    val container: Color,
    val hazyMist: Color,
    val glow: Color,
    val glowMid: Color,
    val rimBottom: Color,
    val dotLight: Color,
    val dotDark: Color,
)

@Composable
private fun rememberPillLoadingColors(
    container: Color,
    primary: Color,
    tertiary: Color,
): PillLoadingColors = remember(container, primary, tertiary) {
    val isLightPrimary = primary.luminance() > 0.45f
    PillLoadingColors(
        container = container,
        hazyMist = if (isLightPrimary) {
            lerp(primary, Color.White, 0.08f)
        } else {
            lerp(primary, Color.White, 0.12f)
        },
        glow = primary,
        glowMid = lerp(primary, tertiary, 0.30f),
        rimBottom = lerp(primary, Color.White, 0.52f),
        dotLight = lerp(primary, Color.White, 0.78f),
        dotDark = lerp(tertiary, Color.Black, 0.18f),
    )
}

fun Modifier.pillTertiaryLoadingGradient(
    isLoading: Boolean,
    onAnimationCompleted: () -> Unit,
    containerColor: Color = Color.Unspecified,
): Modifier = composed {
    val animatedPrimary by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "pillShaderPrimary"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.tertiary,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "pillShaderTertiary"
    )
    val resolvedContainer = if (containerColor == Color.Unspecified) {
        animatedPrimary
    } else {
        containerColor
    }
    val animation = rememberPillLoadingAnimation(
        isLoading = isLoading,
        onAnimationCompleted = onAnimationCompleted,
    )
    val colors = rememberPillLoadingColors(
        container = resolvedContainer,
        primary = animatedPrimary,
        tertiary = animatedTertiary,
    )
    val blurNode = remember { RenderNode("PillLoadingBlur") }
    val blurPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val layerPaint = remember { androidx.compose.ui.graphics.Paint() }

    this.drawWithContent {
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            val k = animation.intensity.value
            val phase = (animation.time.value % LOOP_PERIOD_S) / LOOP_PERIOD_S
            val cornerRadiusPx = h / 2f

            if (k > 0.001f) {
                drawOuterGlow(
                    colors = colors,
                    cornerRadiusPx = cornerRadiusPx,
                    reach = 22.dp,
                    k = k,
                    phase = phase,
                )

                drawBlurredPillBackdrop(
                    blurNode = blurNode,
                    blurPaint = blurPaint,
                    colors = colors,
                    cornerRadiusPx = cornerRadiusPx,
                    blurRadius = 15.dp,
                    k = k,
                )
            }

            drawMaskedPillFill(
                colors = colors,
                cornerRadiusPx = cornerRadiusPx,
                layerPaint = layerPaint,
                k = k,
            )

            if (k > 0.001f) {
                drawPillTexture(
                    colors = colors,
                    cornerRadiusPx = cornerRadiusPx,
                    k = k,
                    phase = phase,
                )
            }
        }

        drawContent()
    }
}

private fun DrawScope.drawOuterGlow(
    colors: PillLoadingColors,
    cornerRadiusPx: Float,
    reach: Dp,
    k: Float,
    phase: Float,
) {
    val pulse = 0.5f + 0.5f * sin(TWO_PI * 2f * phase)
    val strength = k * (0.45f + 0.48f * pulse)
    val reachPx = reach.toPx()
    val steps = 16
    val step = reachPx / steps

    val brush = Brush.horizontalGradient(
        colors = listOf(colors.glow, colors.glowMid, colors.glow),
        startX = 0f,
        endX = size.width,
    )

    for (i in 0 until steps) {
        val e = step * i + step / 2f
        val falloff = 1f - (i + 0.5f) / steps
        val cubicFalloff = falloff * falloff * falloff
        drawRoundRect(
            brush = brush,
            topLeft = Offset(-e, -e * 0.9f),
            size = Size(size.width + 2f * e, size.height + 1.9f * e),
            cornerRadius = CornerRadius(cornerRadiusPx + e),
            style = Stroke(width = step * 1.15f),
            alpha = (strength * cubicFalloff * 0.55f).coerceIn(0f, 1f),
        )
    }
}

private fun DrawScope.drawBlurredPillBackdrop(
    blurNode: RenderNode,
    blurPaint: Paint,
    colors: PillLoadingColors,
    cornerRadiusPx: Float,
    blurRadius: Dp,
    k: Float,
) {
    val w = size.width.roundToInt().coerceAtLeast(1)
    val h = size.height.roundToInt().coerceAtLeast(1)
    val blurPx = (blurRadius.toPx() * k).coerceAtLeast(0.5f)
    val pad = (blurRadius.toPx() * 3f).roundToInt()

    blurNode.setPosition(-pad, -pad, w + pad, h + pad)
    blurNode.setRenderEffect(
        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
    )
    blurNode.alpha = k.coerceIn(0f, 1f)

    blurPaint.color = colors.hazyMist.toArgb()
    val canvas = blurNode.beginRecording(w + pad * 2, h + pad * 2)
    canvas.translate(pad.toFloat(), pad.toFloat())

    val sideOutset = 1.dp.toPx() * k
    val topLift = 5.dp.toPx() * k
    val bottomPull = 8.dp.toPx() * k
    val r = (cornerRadiusPx + sideOutset).coerceAtLeast(0f)

    canvas.drawRoundRect(
        sideOutset * -1f,
        -topLift,
        size.width + sideOutset,
        size.height - bottomPull,
        r,
        r,
        blurPaint,
    )
    blurNode.endRecording()

    drawContext.canvas.nativeCanvas.drawRenderNode(blurNode)
}

private fun DrawScope.drawMaskedPillFill(
    colors: PillLoadingColors,
    cornerRadiusPx: Float,
    layerPaint: androidx.compose.ui.graphics.Paint,
    k: Float,
) {
    if (k <= 0.001f) {
        drawRoundRect(
            color = colors.container,
            cornerRadius = CornerRadius(cornerRadiusPx),
        )
        return
    }

    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
    drawRoundRect(
        color = colors.container,
        cornerRadius = CornerRadius(cornerRadiusPx),
    )
    drawRect(
        brush = Brush.verticalGradient(
            0.00f to Color.Black.copy(alpha = 1f - k),
            0.08f to Color.Black.copy(alpha = 1f - 0.55f * k),
            0.18f to Color.Black,
            1.00f to Color.Black,
        ),
        blendMode = BlendMode.DstIn,
    )
    canvas.restore()
}

private fun DrawScope.drawPillTexture(
    colors: PillLoadingColors,
    cornerRadiusPx: Float,
    k: Float,
    phase: Float,
) {
    val r = cornerRadiusPx.coerceAtMost(min(size.width, size.height) / 2f)

    val sideInset = 0.75.dp.toPx()
    val topInset = 2.dp.toPx()
    val bottomInset = 0.5.dp.toPx()
    val shape = Path().apply {
        addRoundRect(
            RoundRect(
                sideInset,
                topInset,
                size.width - sideInset,
                size.height - bottomInset,
                CornerRadius((r - sideInset).coerceAtLeast(0f)),
            )
        )
    }

    clipPath(shape) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.66f to Color.Transparent,
                1.00f to colors.rimBottom.copy(alpha = 0.48f * k),
            ),
            cornerRadius = CornerRadius(r),
        )

        drawHalftoneLattice(
            colors = colors,
            pitch = 5.8.dp.toPx(),
            maxRadius = 1.45.dp.toPx(),
            baseRimWidth = 30.dp.toPx(),
            cornerR = r,
            k = k,
            phase = phase,
        )
    }
}

private fun DrawScope.drawHalftoneLattice(
    colors: PillLoadingColors,
    pitch: Float,
    maxRadius: Float,
    baseRimWidth: Float,
    cornerR: Float,
    k: Float,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val rowStep = pitch / 2f
    val cols = (w / pitch).toInt() + 3
    val rows = (h / rowStep).toInt() + 2
    val halfW = w / 2f
    val halfH = h / 2f
    val innerX = halfW - cornerR
    val innerY = halfH - cornerR

    val shift = pitch * (1f - phase)
    val featherTop = 12.dp.toPx()
    val featherBottom = 1.2.dp.toPx()
    val minVisibleRadius = 0.25.dp.toPx()

    val wavelengthA = 90.dp.toPx()
    val wavelengthB = 120.dp.toPx()

    for (j in 0 until rows) {
        val y = j * rowStep
        val ny = (y / h).coerceIn(0f, 1f)
        val rowOffset = if (j % 2 == 0) 0f else pitch / 2f
        val feather = featherTop + (featherBottom - featherTop) * ny

        for (i in -1 until cols) {
            val x = i * pitch + rowOffset + shift
            val nx = (x / w).coerceIn(0f, 1f)

            val endCap = abs(nx - 0.5f) * 2f
            val localRimWidth = baseRimWidth * (0.30f + 0.70f * endCap * endCap)

            val px = abs(x - halfW) - innerX
            val py = abs(y - halfH) - innerY
            val d = hypot(max(px, 0f), max(py, 0f)) + min(max(px, py), 0f) - cornerR
            val depth = -d
            if (depth <= 0f || depth > localRimWidth) continue

            val radial = (1f - depth / localRimWidth).coerceIn(0f, 1f)
            val edgeSoft = (depth / feather).coerceIn(0f, 1f)

            val invX = 1f - nx
            val leftCorner = invX * invX * (0.30f + 0.70f * ny)
            val rightCorner = nx * nx * (0.18f + 0.82f * ny)
            val bottomWeight = ny * ny * 0.55f
            val topFade = ((ny - 0.18f) / 0.55f).coerceIn(0f, 1f)
            val spatialEnvelope = ((0.10f + bottomWeight + 1.45f * (leftCorner + 0.80f * rightCorner)) * topFade)
                .coerceIn(0f, 1.30f)

            val waveA = 0.5f + 0.5f * sin(TWO_PI * (x / wavelengthA + 3f * phase))
            val waveB = 0.5f + 0.5f * sin(TWO_PI * (x / wavelengthB - 2f * phase))
            val waveEnvelope = (0.28f + 0.48f * waveA + 0.34f * waveB).coerceIn(0f, 1.15f)

            val strength = (radial * edgeSoft * spatialEnvelope * waveEnvelope).coerceIn(0f, 1f)

            val dotRadius = maxRadius * (0.08f + 0.92f * strength)
            if (dotRadius < minVisibleRadius) continue

            val dotAlpha = (k * (0.32f + 0.68f * strength)).coerceIn(0f, 0.96f)
            if (dotAlpha < 0.03f) continue

            val waveTintMix = ((waveB / (waveA + waveB + 0.001f)) * 0.60f).coerceIn(0f, 1f)
            val dotColor = lerp(colors.dotLight, colors.dotDark, waveTintMix)

            drawCircle(
                color = dotColor.copy(alpha = dotAlpha),
                radius = dotRadius,
                center = Offset(x, y),
            )
        }
    }
}