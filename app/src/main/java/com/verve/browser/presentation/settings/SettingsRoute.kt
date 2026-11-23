package com.verve.browser.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.verve.browser.domain.model.AdBlockMode
import com.verve.browser.presentation.downloads.DownloadsRoute
import com.verve.browser.presentation.update.AppUpdateController

private enum class SettingsDestination {
    MAIN,
    PRIVACY_SECURITY,
    THEME,
    DOWNLOADS,
    ABOUT_VERVE
}

@Composable
fun SettingsFlow(
    updateController: AppUpdateController,
    onExitSettings: () -> Unit,
    adBlockMode: AdBlockMode = AdBlockMode.STANDARD,
    onSelectAdBlockMode: (AdBlockMode) -> Unit = {},
    openAboutVerveDirectly: Boolean = false,
    autoStartUpdate: Boolean = false,
    onAutoStartUpdateHandled: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {}
) {
    var destination by remember {
        mutableStateOf(
            if (openAboutVerveDirectly) {
                SettingsDestination.ABOUT_VERVE
            } else {
                SettingsDestination.MAIN
            }
        )
    }
    val updateState by updateController.uiState.collectAsState()

    BackHandler {
        if (destination != SettingsDestination.MAIN) {
            destination = SettingsDestination.MAIN
        } else {
            onExitSettings()
        }
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            if (targetState != SettingsDestination.MAIN) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "settingsNavigation"
    ) { screen ->
        when (screen) {
            SettingsDestination.MAIN -> {
                SettingsScreen(
                    hasUpdateAvailable = updateState.availableUpdate != null,
                    adBlockMode = adBlockMode,
                    onNavigateBack = onExitSettings,
                    onOpenPrivacySecurity = { destination = SettingsDestination.PRIVACY_SECURITY },
                    onOpenTheme = { destination = SettingsDestination.THEME },
                    onOpenDownloads = { destination = SettingsDestination.DOWNLOADS },
                    onOpenAboutVerve = { destination = SettingsDestination.ABOUT_VERVE }
                )
            }
            SettingsDestination.PRIVACY_SECURITY -> {
                PrivacySecuritySettingsScreen(
                    adBlockMode = adBlockMode,
                    onSelectAdBlockMode = onSelectAdBlockMode,
                    onNavigateBack = { destination = SettingsDestination.MAIN }
                )
            }
            SettingsDestination.THEME -> {
                ThemeSettingsScreen(
                    onNavigateBack = { destination = SettingsDestination.MAIN }
                )
            }
            SettingsDestination.DOWNLOADS -> {
                DownloadsRoute(
                    onNavigateBack = { destination = SettingsDestination.MAIN },
                    onOpenUrl = { url ->
                        onExitSettings()
                        onOpenUrl(url)
                    }
                )
            }
            SettingsDestination.ABOUT_VERVE -> {
                LaunchedEffect(autoStartUpdate) {
                    if (autoStartUpdate) {
                        onAutoStartUpdateHandled()
                        updateController.startDownloadAndInstall()
                    }
                }

                AboutVerveScreen(
                    updateState = updateState,
                    onCheckForUpdates = { updateController.checkForUpdates(userInitiated = true) },
                    onUpdateClick = updateController::startDownloadAndInstall,
                    onCancelDownload = updateController::cancelDownload,
                    onNavigateBack = { destination = SettingsDestination.MAIN }
                )
            }
        }
    }
}