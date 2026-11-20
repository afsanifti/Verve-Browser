package com.verve.browser.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.verve.browser.data.network.QuicNetworkManager
import com.verve.browser.presentation.browser.util.DefaultBrowserHelper
import com.verve.browser.presentation.theme.rememberThemeConfig

@Composable
fun SettingsScreen(
    hasUpdateAvailable: Boolean,
    onNavigateBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAboutVerve: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val themeConfig = rememberThemeConfig()
    val isQuicEnabled by QuicNetworkManager.isQuicEnabled.collectAsState()

    var isDefaultBrowser by remember {
        mutableStateOf(DefaultBrowserHelper.isDefaultBrowser(context))
    }

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultBrowser = DefaultBrowserHelper.isDefaultBrowser(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultBrowser = DefaultBrowserHelper.isDefaultBrowser(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        QuicNetworkManager.initialize(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Profile Section
            SettingsSectionLabel(text = "Profile")
            ExpressiveSegmentedGroup(
                items = listOf(
                    ExpressiveMenuItem(
                        title = "Verve Profile",
                        subtitle = "Sign in to sync",
                        leadingContent = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Icon(
                                    imageVector = SettingsSymbols.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Browser Section
            SettingsSectionLabel(text = "Browser")
            ExpressiveSegmentedGroup(
                items = listOf(
                    ExpressiveMenuItem(
                        title = "Search Engine",
                        subtitle = "Google"
                    ),
                    ExpressiveMenuItem(
                        title = "Default Browser",
                        subtitle = if (isDefaultBrowser) {
                            "Verve is your default browser"
                        } else {
                            "Set Verve as your default browser"
                        },
                        onClick = {
                            defaultBrowserLauncher.launch(
                                DefaultBrowserHelper.createRequestDefaultBrowserIntent(context)
                            )
                        }
                    ),
                    ExpressiveMenuItem(
                        title = "QUIC / HTTP/3",
                        subtitle = if (isQuicEnabled) {
                            "Faster encrypted UDP connections enabled"
                        } else {
                            "Use standard HTTP/2 over TCP"
                        },
                        trailingContent = {
                            Switch(
                                checked = isQuicEnabled,
                                onCheckedChange = { enabled ->
                                    QuicNetworkManager.setQuicEnabled(context, enabled)
                                }
                            )
                        },
                        onClick = {
                            QuicNetworkManager.setQuicEnabled(context, !isQuicEnabled)
                        }
                    )
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // General Section
            SettingsSectionLabel(text = "General")
            ExpressiveSegmentedGroup(
                items = listOf(
                    ExpressiveMenuItem(
                        title = "Theme",
                        subtitle = themeConfig.summaryLabel(),
                        onClick = onOpenTheme
                    ),
                    ExpressiveMenuItem(
                        title = "Downloads",
                        onClick = onOpenDownloads
                    ),
                    ExpressiveMenuItem(
                        title = "About Verve",
                        trailingContent = if (hasUpdateAvailable) {
                            {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            null
                        },
                        onClick = onOpenAboutVerve
                    )
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}