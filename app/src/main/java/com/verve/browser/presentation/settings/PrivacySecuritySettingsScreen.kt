package com.verve.browser.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verve.browser.domain.model.AdBlockMode

@Composable
fun PrivacySecuritySettingsScreen(
    adBlockMode: AdBlockMode,
    onSelectAdBlockMode: (AdBlockMode) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacy & Security",
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
                .verticalScroll(rememberScrollState())
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            SettingsSectionLabel(text = "Block trackers and ads")
            ExpressiveSegmentedGroup(
                items = listOf(
                    ExpressiveMenuItem(
                        title = "Standard",
                        subtitle = "Blocks third-party ads, trackers, and cross-site scripts while preserving site compatibility",
                        selected = adBlockMode == AdBlockMode.STANDARD,
                        trailingContent = {
                            RadioButton(
                                selected = adBlockMode == AdBlockMode.STANDARD,
                                onClick = null
                            )
                        },
                        onClick = { onSelectAdBlockMode(AdBlockMode.STANDARD) }
                    ),
                    ExpressiveMenuItem(
                        title = "Aggressive",
                        subtitle = "Blocks first-party and CNAME-cloaked trackers plus stricter cosmetic filtering",
                        selected = adBlockMode == AdBlockMode.AGGRESSIVE,
                        trailingContent = {
                            RadioButton(
                                selected = adBlockMode == AdBlockMode.AGGRESSIVE,
                                onClick = null
                            )
                        },
                        onClick = { onSelectAdBlockMode(AdBlockMode.AGGRESSIVE) }
                    )
                )
            )
        }
    }
}