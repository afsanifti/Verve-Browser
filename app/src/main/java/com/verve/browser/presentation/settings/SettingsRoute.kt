package com.verve.browser.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.verve.browser.presentation.update.AppUpdateController

private enum class SettingsDestination {
    MAIN,
    THEME,
    ABOUT_VERVE
}

@Composable
fun SettingsFlow(
    updateController: AppUpdateController,
    onExitSettings: () -> Unit
) {
    var destination by remember { mutableStateOf(SettingsDestination.MAIN) }
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
                    onNavigateBack = onExitSettings,
                    onOpenTheme = { destination = SettingsDestination.THEME },
                    onOpenAboutVerve = { destination = SettingsDestination.ABOUT_VERVE }
                )
            }
            SettingsDestination.THEME -> {
                ThemeSettingsScreen(
                    onNavigateBack = { destination = SettingsDestination.MAIN }
                )
            }
            SettingsDestination.ABOUT_VERVE -> {
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