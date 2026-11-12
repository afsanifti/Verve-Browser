package com.verve.browser.presentation.browser.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.DropShadow(
    fillColor: Color,
    shadowColor: Color = Color.Black.copy(alpha = 0.25f),
    offsetX: Dp = 0.dp,
    offsetY: Dp = 2.dp,
    blurRadius: Dp = 8.dp,
    spread: Dp = 0.dp,
    cornerRadius: Dp? = null
): Modifier = this.drawBehind {
    val spreadPx = spread.toPx()
    val offsetXPx = offsetX.toPx()
    val offsetYPx = offsetY.toPx()
    val blurRadiusPx = blurRadius.toPx()
    val insetPx = 0.5.dp.toPx()

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = fillColor
        }
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.setShadowLayer(
            blurRadiusPx,
            offsetXPx,
            offsetYPx,
            shadowColor.toArgb()
        )

        val left = -spreadPx + insetPx
        val top = -spreadPx + insetPx
        val right = size.width + spreadPx - insetPx
        val bottom = size.height + spreadPx - insetPx
        val radiusPx = cornerRadius?.toPx() ?: ((bottom - top) / 2f)

        canvas.drawRoundRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = paint
        )
    }
}