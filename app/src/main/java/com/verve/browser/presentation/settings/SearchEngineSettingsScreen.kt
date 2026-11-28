package com.verve.browser.presentation.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verve.browser.data.preferences.SearchEnginePreferences

@Composable
fun SearchEngineSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val selectedEngine by SearchEnginePreferences.selectedEngine.collectAsState()

    LaunchedEffect(Unit) {
        SearchEnginePreferences.initialize(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search Engine",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "__header__") {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsSectionLabel(text = "Default search engine")
                    Text(
                        text = "Used when searching from the address bar or opening new searches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                    )
                }
            }

            items(
                items = SearchEnginePreferences.supportedEngines,
                key = { it.id }
            ) { engine ->
                val isSelected = engine.id == selectedEngine.id

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable {
                            SearchEnginePreferences.setSearchEngine(context, engine)
                        },
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 14.dp,
                                end = 22.dp,
                                top = 12.dp,
                                bottom = 12.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchEngineLogo(
                            engineId = engine.id,
                            isSelected = isSelected,
                            modifier = Modifier.size(38.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = engine.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = engine.description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineLogo(
    engineId: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fallbackTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(badgeContainerColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            when (engineId.lowercase()) {
                "google" -> {
                    val strokeW = w * 0.19f
                    val arcInset = strokeW * 0.55f
                    val arcSize = Size(w - arcInset * 2f, h - arcInset * 2f)
                    val topLeft = Offset(arcInset, arcInset)

                    // Red top arc
                    drawArc(
                        color = Color(0xFFEA4335),
                        startAngle = 215f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    // Yellow left arc
                    drawArc(
                        color = Color(0xFFFBBC05),
                        startAngle = 145f,
                        sweepAngle = 72f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    // Green bottom arc
                    drawArc(
                        color = Color(0xFF34A853),
                        startAngle = 42f,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    // Blue right arc + horizontal bar
                    drawArc(
                        color = Color(0xFF4285F4),
                        startAngle = -8f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                    )
                    drawLine(
                        color = Color(0xFF4285F4),
                        start = Offset(cx, cy),
                        end = Offset(w - arcInset * 0.5f, cy),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Butt
                    )
                }

                "bing" -> {
                    // Microsoft Bing ribbon mark
                    val stem = Path().apply {
                        moveTo(w * 0.28f, h * 0.12f)
                        lineTo(w * 0.44f, h * 0.21f)
                        lineTo(w * 0.44f, h * 0.67f)
                        lineTo(w * 0.75f, h * 0.50f)
                        lineTo(w * 0.60f, h * 0.43f)
                        lineTo(w * 0.55f, h * 0.30f)
                        lineTo(w * 0.84f, h * 0.42f)
                        lineTo(w * 0.84f, h * 0.58f)
                        lineTo(w * 0.44f, h * 0.86f)
                        lineTo(w * 0.28f, h * 0.76f)
                        close()
                    }
                    drawPath(path = stem, color = Color(0xFF008373))

                    val lowerFold = Path().apply {
                        moveTo(w * 0.44f, h * 0.67f)
                        lineTo(w * 0.84f, h * 0.46f)
                        lineTo(w * 0.84f, h * 0.58f)
                        lineTo(w * 0.44f, h * 0.86f)
                        close()
                    }
                    drawPath(path = lowerFold, color = Color(0xFF00B294))
                }

                "duckduckgo" -> {
                    // DuckDuckGo orange badge + white duck silhouette & green bow-tie
                    drawCircle(color = Color(0xFFDE5833), radius = w * 0.48f)

                    val duckHead = Path().apply {
                        moveTo(w * 0.36f, h * 0.72f)
                        cubicTo(w * 0.34f, h * 0.52f, w * 0.33f, h * 0.30f, w * 0.48f, h * 0.25f)
                        cubicTo(w * 0.62f, h * 0.22f, w * 0.68f, h * 0.34f, w * 0.63f, h * 0.46f)
                        cubicTo(w * 0.60f, h * 0.54f, w * 0.62f, h * 0.64f, w * 0.64f, h * 0.72f)
                        close()
                    }
                    drawPath(path = duckHead, color = Color.White)

                    // Yellow bill
                    drawRoundRect(
                        color = Color(0xFFFEDB29),
                        topLeft = Offset(w * 0.56f, h * 0.38f),
                        size = Size(w * 0.26f, h * 0.11f),
                        cornerRadius = CornerRadius(h * 0.055f, h * 0.055f)
                    )
                    // Eye
                    drawCircle(
                        color = Color(0xFF2B2B2B),
                        radius = w * 0.038f,
                        center = Offset(w * 0.53f, h * 0.34f)
                    )
                    // Green bow-tie
                    val bowTie = Path().apply {
                        moveTo(w * 0.42f, h * 0.66f)
                        lineTo(w * 0.52f, h * 0.71f)
                        lineTo(w * 0.62f, h * 0.66f)
                        lineTo(w * 0.62f, h * 0.76f)
                        lineTo(w * 0.52f, h * 0.71f)
                        lineTo(w * 0.42f, h * 0.76f)
                        close()
                    }
                    drawPath(path = bowTie, color = Color(0xFF53A845))
                }

                "brave" -> {
                    // Brave lion shield gradient-styled emblem
                    val shield = Path().apply {
                        moveTo(w * 0.24f, h * 0.16f)
                        lineTo(w * 0.76f, h * 0.16f)
                        lineTo(w * 0.84f, h * 0.34f)
                        lineTo(w * 0.74f, h * 0.72f)
                        lineTo(cx, h * 0.90f)
                        lineTo(w * 0.26f, h * 0.72f)
                        lineTo(w * 0.16f, h * 0.34f)
                        close()
                    }
                    drawPath(path = shield, color = Color(0xFFFB542B))

                    val rightShade = Path().apply {
                        moveTo(cx, h * 0.16f)
                        lineTo(w * 0.76f, h * 0.16f)
                        lineTo(w * 0.84f, h * 0.34f)
                        lineTo(w * 0.74f, h * 0.72f)
                        lineTo(cx, h * 0.90f)
                        close()
                    }
                    drawPath(path = rightShade, color = Color(0xFFFF2000))

                    // Inner lion face lines
                    val lionMark = Path().apply {
                        moveTo(w * 0.34f, h * 0.34f)
                        lineTo(cx, h * 0.44f)
                        lineTo(w * 0.66f, h * 0.34f)
                        moveTo(w * 0.38f, h * 0.54f)
                        lineTo(cx, h * 0.68f)
                        lineTo(w * 0.62f, h * 0.54f)
                    }
                    drawPath(
                        path = lionMark,
                        color = Color.White,
                        style = Stroke(
                            width = w * 0.08f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                "startpage" -> {
                    // Startpage blue/red pinwheel & search dot
                    drawArc(
                        color = Color(0xFF2B6CB0),
                        startAngle = 120f,
                        sweepAngle = 210f,
                        useCenter = false,
                        topLeft = Offset(w * 0.16f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.68f),
                        style = Stroke(width = w * 0.15f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFE53E3E),
                        startAngle = -35f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(w * 0.16f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.68f),
                        style = Stroke(width = w * 0.15f, cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = Color(0xFF2B6CB0),
                        radius = w * 0.11f,
                        center = Offset(cx, cy)
                    )
                }

                "ecosia" -> {
                    // Ecosia green globe + sprouting leaf
                    drawCircle(
                        color = Color(0xFF369A42),
                        radius = w * 0.44f,
                        center = Offset(cx, h * 0.54f)
                    )
                    val leaf = Path().apply {
                        moveTo(cx, h * 0.62f)
                        cubicTo(w * 0.26f, h * 0.48f, w * 0.30f, h * 0.22f, w * 0.66f, h * 0.18f)
                        cubicTo(w * 0.68f, h * 0.44f, w * 0.56f, h * 0.58f, cx, h * 0.62f)
                        close()
                    }
                    drawPath(path = leaf, color = Color(0xFF8CD24E))
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, h * 0.72f),
                        end = Offset(w * 0.56f, h * 0.32f),
                        strokeWidth = w * 0.065f,
                        cap = StrokeCap.Round
                    )
                }

                "qwant" -> {
                    // Qwant multicolor Q ring + tail
                    val r = w * 0.33f
                    val ringStroke = w * 0.16f
                    val tl = Offset(cx - r, cy - r)
                    val sz = Size(r * 2f, r * 2f)

                    drawArc(Color(0xFF4285F4), 180f, 95f, false, tl, sz, style = Stroke(ringStroke))
                    drawArc(Color(0xFFEA4335), 270f, 95f, false, tl, sz, style = Stroke(ringStroke))
                    drawArc(Color(0xFFFBBC05), 0f, 95f, false, tl, sz, style = Stroke(ringStroke))
                    drawArc(Color(0xFF34A853), 90f, 95f, false, tl, sz, style = Stroke(ringStroke))

                    drawLine(
                        color = Color(0xFF4285F4),
                        start = Offset(cx + r * 0.55f, cy + r * 0.55f),
                        end = Offset(w * 0.86f, h * 0.86f),
                        strokeWidth = ringStroke,
                        cap = StrokeCap.Round
                    )
                }

                "yahoo" -> {
                    // Yahoo! purple Y + exclamation mark
                    val yPath = Path().apply {
                        moveTo(w * 0.16f, h * 0.24f)
                        lineTo(w * 0.38f, h * 0.56f)
                        lineTo(w * 0.60f, h * 0.24f)
                        moveTo(w * 0.38f, h * 0.56f)
                        lineTo(w * 0.38f, h * 0.82f)
                    }
                    drawPath(
                        path = yPath,
                        color = Color(0xFF6001D2),
                        style = Stroke(
                            width = w * 0.15f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Tilted exclamation mark
                    drawLine(
                        color = Color(0xFF6001D2),
                        start = Offset(w * 0.78f, h * 0.22f),
                        end = Offset(w * 0.71f, h * 0.58f),
                        strokeWidth = w * 0.13f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color(0xFF6001D2),
                        radius = w * 0.075f,
                        center = Offset(w * 0.68f, h * 0.77f)
                    )
                }

                "yandex" -> {
                    // Yandex red circle + white Cyrillic Ya (Я)
                    drawCircle(color = Color(0xFFFC3F1D), radius = w * 0.48f)

                    val yaPath = Path().apply {
                        moveTo(w * 0.60f, h * 0.22f)
                        lineTo(w * 0.60f, h * 0.78f)
                        moveTo(w * 0.60f, h * 0.22f)
                        lineTo(w * 0.46f, h * 0.22f)
                        cubicTo(w * 0.32f, h * 0.22f, w * 0.32f, h * 0.48f, w * 0.46f, h * 0.48f)
                        lineTo(w * 0.60f, h * 0.48f)
                        moveTo(w * 0.47f, h * 0.48f)
                        lineTo(w * 0.34f, h * 0.78f)
                    }
                    drawPath(
                        path = yaPath,
                        color = Color.White,
                        style = Stroke(
                            width = w * 0.115f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                "kagi" -> {
                    // Kagi yellow circle + iconic dog/key mark
                    drawCircle(color = Color(0xFFFFB319), radius = w * 0.48f)

                    val kPath = Path().apply {
                        moveTo(w * 0.35f, h * 0.24f)
                        lineTo(w * 0.35f, h * 0.76f)
                        moveTo(w * 0.67f, h * 0.26f)
                        lineTo(w * 0.38f, h * 0.52f)
                        lineTo(w * 0.69f, h * 0.76f)
                    }
                    drawPath(
                        path = kPath,
                        color = Color(0xFF1F1F1F),
                        style = Stroke(
                            width = w * 0.13f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                else -> {
                    // Generic magnifying glass fallback
                    drawCircle(
                        color = fallbackTint,
                        radius = w * 0.28f,
                        center = Offset(w * 0.44f, h * 0.44f),
                        style = Stroke(width = w * 0.12f)
                    )
                    drawLine(
                        color = fallbackTint,
                        start = Offset(w * 0.64f, h * 0.64f),
                        end = Offset(w * 0.84f, h * 0.84f),
                        strokeWidth = w * 0.12f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}