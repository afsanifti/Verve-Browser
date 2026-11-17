package com.verve.browser.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verve.browser.presentation.theme.AppDarkMode
import com.verve.browser.presentation.theme.AppThemeStyle
import com.verve.browser.presentation.theme.ThemePreferences
import com.verve.browser.presentation.theme.rememberThemeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val themeConfig = rememberThemeConfig()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Theme",
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
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            // Segment 1: Light, Dark, or System default
            SettingsSectionLabel(text = "Appearance")
            ExpressiveSegmentedGroup(
                items = AppDarkMode.entries.map { mode ->
                    val isSelected = themeConfig.darkMode == mode
                    ExpressiveMenuItem(
                        title = mode.title,
                        subtitle = mode.subtitle,
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        },
                        onClick = { ThemePreferences.setDarkMode(context, mode) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Segment 2: System theme vs MaterialKolor Expressive styles
            SettingsSectionLabel(text = "Color Style")
            ExpressiveSegmentedGroup(
                items = AppThemeStyle.entries.map { style ->
                    val isSelected = themeConfig.style == style
                    ExpressiveMenuItem(
                        title = style.title,
                        subtitle = style.subtitle,
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        },
                        onClick = { ThemePreferences.setThemeStyle(context, style) }
                    )
                }
            )
        }
    }
}