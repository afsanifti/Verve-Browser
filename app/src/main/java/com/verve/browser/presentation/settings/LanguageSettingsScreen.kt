package com.verve.browser.presentation.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Drag_handle
import com.composables.icons.materialsymbols.rounded.More_vert
import com.verve.browser.data.preferences.LanguagePreferences
import com.verve.browser.presentation.browser.util.PageTranslationManager
import com.verve.browser.presentation.browser.util.TranslateLanguageOption

private enum class LanguagePickerSheetType {
    APP_LANGUAGE,
    ADD_PREFERRED_LANGUAGE,
    TRANSLATE_INTO_LANGUAGE,
    AUTO_TRANSLATE_LANGUAGES,
    NEVER_TRANSLATE_LANGUAGES
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanguageSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        LanguagePreferences.initialize(context)
    }

    val appLanguage by LanguagePreferences.appLanguage.collectAsState()
    val preferredLanguages by LanguagePreferences.preferredLanguages.collectAsState()
    val offerToTranslate by LanguagePreferences.offerToTranslate.collectAsState()
    val defaultLanguage by LanguagePreferences.defaultLanguage.collectAsState()
    val autoTranslateLanguages by LanguagePreferences.autoTranslateLanguages.collectAsState()
    val neverTranslateLanguages by LanguagePreferences.neverTranslateLanguages.collectAsState()

    var activeSheet by remember { mutableStateOf<LanguagePickerSheetType?>(null) }
    var openMenuLanguage by remember { mutableStateOf<String?>(null) }

    // Local mutable list for smooth 60/120fps drag reordering
    val displayLanguages = remember { mutableStateListOf<String>() }
    var draggingLanguage by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightsPx = remember { mutableStateMapOf<String, Int>() }
    val gapPx = with(density) { ListItemDefaults.SegmentedGap.toPx() }

    LaunchedEffect(preferredLanguages, draggingLanguage) {
        if (draggingLanguage == null && displayLanguages.toList() != preferredLanguages) {
            displayLanguages.clear()
            displayLanguages.addAll(preferredLanguages)
        }
    }

    val segmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    val activeDragColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    val singleCardShapes = ListItemDefaults.shapes(
        shape = RoundedCornerShape(20.dp)
    )

    val deviceRegionLabel = remember { LanguagePreferences.deviceLanguageWithRegionLabel() }
    val appLanguageSubtitle = appLanguage ?: deviceRegionLabel

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Languages",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState(), enabled = draggingLanguage == null)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp
                )
        ) {
            // 1. Verve's language
            SettingsSectionLabel(text = "Verve's language")
            SegmentedListItem(
                onClick = { activeSheet = LanguagePickerSheetType.APP_LANGUAGE },
                shapes = singleCardShapes,
                colors = segmentedColors,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Text(
                        text = if (appLanguage == null) "Current device language" else "App language",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                supportingContent = {
                    Text(
                        text = appLanguageSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 2. Preferred languages (drag-to-reorder via `=` handle + `⋮` overflow menu)
            SettingsSectionLabel(text = "Preferred languages")
            Text(
                text = "Let websites know the languages you speak. They'll show content in those languages, when possible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
            )

            val preferredTotalRows = displayLanguages.size + 1
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                displayLanguages.forEachIndexed { index, langName ->
                    key(langName) {
                        val isDragging = draggingLanguage == langName
                        val currentIndex by rememberUpdatedState(index)
                        val nativeName = PageTranslationManager.supportedLanguages
                            .firstOrNull { it.displayName.equals(langName, ignoreCase = true) }
                            ?.nativeName

                        val animatedOffsetY by animateFloatAsState(
                            targetValue = if (isDragging) dragOffsetY else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh
                            ),
                            label = "dragOffsetY"
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.02f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dragScale"
                        )
                        val animatedElevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dragElevation"
                        )

                        val itemShapes = if (isDragging) {
                            singleCardShapes
                        } else {
                            ListItemDefaults.segmentedShapes(
                                index = index,
                                count = preferredTotalRows
                            )
                        }

                        SegmentedListItem(
                            onClick = {
                                if (draggingLanguage == null) {
                                    openMenuLanguage = langName
                                }
                            },
                            shapes = itemShapes,
                            colors = if (isDragging) activeDragColors else segmentedColors,
                            contentPadding = PaddingValues(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .onSizeChanged { rowHeightsPx[langName] = it.height }
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else animatedOffsetY
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    shadowElevation = with(density) { animatedElevation.toPx() }
                                },
                            leadingContent = {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .pointerInput(langName) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    if (displayLanguages.size > 1) {
                                                        openMenuLanguage = null
                                                        draggingLanguage = langName
                                                        dragStartIndex = currentIndex
                                                        dragOffsetY = 0f
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    if (draggingLanguage != langName) return@detectVerticalDragGestures
                                                    change.consume()
                                                    dragOffsetY += dragAmount

                                                    val idx = displayLanguages.indexOf(langName)
                                                    if (idx == -1) return@detectVerticalDragGestures

                                                    // Swap downward when dragged past half the next row's height
                                                    if (idx < displayLanguages.lastIndex) {
                                                        val nextLang = displayLanguages[idx + 1]
                                                        val stepDown = (rowHeightsPx[nextLang] ?: 140) + gapPx
                                                        if (dragOffsetY > stepDown * 0.5f) {
                                                            displayLanguages.removeAt(idx)
                                                            displayLanguages.add(idx + 1, langName)
                                                            dragOffsetY -= stepDown
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }

                                                    // Swap upward when dragged past half the previous row's height
                                                    if (idx > 0) {
                                                        val prevLang = displayLanguages[idx - 1]
                                                        val stepUp = (rowHeightsPx[prevLang] ?: 140) + gapPx
                                                        if (dragOffsetY < -stepUp * 0.5f) {
                                                            displayLanguages.removeAt(idx)
                                                            displayLanguages.add(idx - 1, langName)
                                                            dragOffsetY += stepUp
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    val finalIndex = displayLanguages.indexOf(langName)
                                                    if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                        LanguagePreferences.reorderPreferredLanguages(
                                                            context = context,
                                                            fromIndex = dragStartIndex,
                                                            toIndex = finalIndex
                                                        )
                                                    }
                                                    draggingLanguage = null
                                                    dragStartIndex = -1
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    val finalIndex = displayLanguages.indexOf(langName)
                                                    if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                        LanguagePreferences.reorderPreferredLanguages(
                                                            context = context,
                                                            fromIndex = dragStartIndex,
                                                            toIndex = finalIndex
                                                        )
                                                    }
                                                    draggingLanguage = null
                                                    dragStartIndex = -1
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Drag_handle,
                                        contentDescription = "Drag to reorder $langName",
                                        tint = if (isDragging) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            content = {
                                Text(
                                    text = langName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = if (!nativeName.isNullOrBlank() && !nativeName.equals(langName, ignoreCase = true)) {
                                {
                                    Text(
                                        text = nativeName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                null
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { openMenuLanguage = langName }) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.More_vert,
                                            contentDescription = "Language options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = openMenuLanguage == langName,
                                        onDismissRequest = { openMenuLanguage = null }
                                    ) {
                                        if (index > 0) {
                                            DropdownMenuItem(
                                                text = { Text("Move up") },
                                                onClick = {
                                                    openMenuLanguage = null
                                                    LanguagePreferences.movePreferredLanguageUp(context, index)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move to top") },
                                                onClick = {
                                                    openMenuLanguage = null
                                                    LanguagePreferences.reorderPreferredLanguages(
                                                        context = context,
                                                        fromIndex = index,
                                                        toIndex = 0
                                                    )
                                                }
                                            )
                                        }
                                        if (index < displayLanguages.lastIndex) {
                                            DropdownMenuItem(
                                                text = { Text("Move down") },
                                                onClick = {
                                                    openMenuLanguage = null
                                                    LanguagePreferences.movePreferredLanguageDown(context, index)
                                                }
                                            )
                                        }
                                        if (displayLanguages.size > 1) {
                                            DropdownMenuItem(
                                                text = { Text("Remove") },
                                                onClick = {
                                                    openMenuLanguage = null
                                                    LanguagePreferences.removePreferredLanguage(context, langName)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // "+ Add language" bottom segment
                SegmentedListItem(
                    onClick = { activeSheet = LanguagePickerSheetType.ADD_PREFERRED_LANGUAGE },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = displayLanguages.size,
                        count = preferredTotalRows
                    ),
                    colors = segmentedColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    content = {
                        Text(
                            text = "Add language",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 3. Translation settings
            SettingsSectionLabel(text = "Translation settings")
            SegmentedListItem(
                onClick = { LanguagePreferences.setOfferToTranslate(context, !offerToTranslate) },
                shapes = singleCardShapes,
                colors = segmentedColors,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Text(
                        text = "Offer to send pages in other languages to Google Translate",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                trailingContent = {
                    Switch(
                        checked = offerToTranslate,
                        onCheckedChange = { enabled ->
                            LanguagePreferences.setOfferToTranslate(context, enabled)
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 4. Advanced
            SettingsSectionLabel(text = "Advanced")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                SegmentedListItem(
                    onClick = { activeSheet = LanguagePickerSheetType.TRANSLATE_INTO_LANGUAGE },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                    colors = segmentedColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = "Translate into this language",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = defaultLanguage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                SegmentedListItem(
                    onClick = { activeSheet = LanguagePickerSheetType.AUTO_TRANSLATE_LANGUAGES },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                    colors = segmentedColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = "Automatically translate these languages",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = if (autoTranslateLanguages.isNotEmpty()) {
                        {
                            Text(
                                text = autoTranslateLanguages.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        null
                    }
                )

                SegmentedListItem(
                    onClick = { activeSheet = LanguagePickerSheetType.NEVER_TRANSLATE_LANGUAGES },
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                    colors = segmentedColors,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = "Don't offer to translate these languages",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = if (neverTranslateLanguages.isNotEmpty()) {
                        {
                            Text(
                                text = neverTranslateLanguages.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    // Bottom sheets for picking / toggling languages
    when (activeSheet) {
        LanguagePickerSheetType.APP_LANGUAGE -> {
            SingleLanguagePickerSheet(
                title = "Verve's language",
                subtitle = "Current device language ($deviceRegionLabel)",
                includeDeviceDefaultOption = true,
                selectedLanguage = appLanguage,
                onSelect = { chosen ->
                    LanguagePreferences.setAppLanguage(context, chosen)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }

        LanguagePickerSheetType.ADD_PREFERRED_LANGUAGE -> {
            val remaining = PageTranslationManager.supportedLanguages.filterNot { opt ->
                preferredLanguages.any { it.equals(opt.displayName, ignoreCase = true) }
            }
            SingleLanguagePickerSheet(
                title = "Add language",
                subtitle = "Choose a language you can read",
                includeDeviceDefaultOption = false,
                selectedLanguage = null,
                customOptions = remaining,
                onSelect = { chosen ->
                    if (!chosen.isNullOrBlank()) {
                        LanguagePreferences.addPreferredLanguage(context, chosen)
                    }
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }

        LanguagePickerSheetType.TRANSLATE_INTO_LANGUAGE -> {
            SingleLanguagePickerSheet(
                title = "Translate into this language",
                subtitle = "Default target language for Google Translate",
                includeDeviceDefaultOption = false,
                selectedLanguage = defaultLanguage,
                onSelect = { chosen ->
                    if (!chosen.isNullOrBlank()) {
                        LanguagePreferences.setDefaultLanguage(context, chosen)
                    }
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }

        LanguagePickerSheetType.AUTO_TRANSLATE_LANGUAGES -> {
            MultiLanguagePickerSheet(
                title = "Automatically translate",
                subtitle = "Pages in these languages will translate automatically without asking",
                initialSelected = autoTranslateLanguages,
                onSave = { updated ->
                    LanguagePreferences.setAutoTranslateLanguages(context, updated)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }

        LanguagePickerSheetType.NEVER_TRANSLATE_LANGUAGES -> {
            MultiLanguagePickerSheet(
                title = "Don't offer to translate",
                subtitle = "Verve won't show the translate prompt for these languages",
                initialSelected = neverTranslateLanguages,
                onSave = { updated ->
                    LanguagePreferences.setNeverTranslateLanguages(context, updated)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null }
            )
        }

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleLanguagePickerSheet(
    title: String,
    subtitle: String,
    includeDeviceDefaultOption: Boolean,
    selectedLanguage: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    customOptions: List<TranslateLanguageOption> = PageTranslationManager.supportedLanguages
) {
    var allowPartialDuringOpen by remember { mutableStateOf(true) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = { target ->
            allowPartialDuringOpen || target != SheetValue.PartiallyExpanded
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.targetValue }
                .collect { target ->
                    when (target) {
                        SheetValue.PartiallyExpanded -> sheetState.expand()
                        SheetValue.Expanded -> allowPartialDuringOpen = false
                        SheetValue.Hidden -> Unit
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (includeDeviceDefaultOption) {
                    item(key = "__device_default__") {
                        val isSelected = selectedLanguage == null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelect(null) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Current device language",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                RadioButton(selected = isSelected, onClick = null)
                            }
                        }
                    }
                }

                items(items = customOptions, key = { it.code }) { option ->
                    val isSelected = selectedLanguage?.equals(option.displayName, ignoreCase = true) == true
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(option.displayName) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = option.nativeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (selectedLanguage != null || includeDeviceDefaultOption) {
                                RadioButton(selected = isSelected, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiLanguagePickerSheet(
    title: String,
    subtitle: String,
    initialSelected: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var allowPartialDuringOpen by remember { mutableStateOf(true) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = { target ->
            allowPartialDuringOpen || target != SheetValue.PartiallyExpanded
        }
    )
    val selectedList = remember(initialSelected) {
        mutableStateListOf<String>().apply { addAll(initialSelected) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.targetValue }
                .collect { target ->
                    when (target) {
                        SheetValue.PartiallyExpanded -> sheetState.expand()
                        SheetValue.Expanded -> allowPartialDuringOpen = false
                        SheetValue.Hidden -> Unit
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSave(selectedList.toList()) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = PageTranslationManager.supportedLanguages, key = { it.code }) { option ->
                    val checked = selectedList.any { it.equals(option.displayName, ignoreCase = true) }
                    val toggle: () -> Unit = {
                        if (checked) {
                            selectedList.removeAll { it.equals(option.displayName, ignoreCase = true) }
                        } else {
                            selectedList.add(option.displayName)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = toggle),
                        shape = RoundedCornerShape(16.dp),
                        color = if (checked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (checked) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = option.nativeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (checked) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            Checkbox(
                                checked = checked,
                                onCheckedChange = { toggle() }
                            )
                        }
                    }
                }
            }
        }
    }
}