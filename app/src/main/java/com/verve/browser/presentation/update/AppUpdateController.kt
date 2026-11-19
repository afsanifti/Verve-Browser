package com.verve.browser.presentation.update

import android.content.Context
import androidx.compose.runtime.Stable
import com.verve.browser.BuildConfig
import com.verve.browser.data.update.ApkInstaller
import com.verve.browser.data.update.GitHubReleaseUpdateRepository
import com.verve.browser.data.update.UpdateNotificationHelper
import com.verve.browser.domain.model.AppUpdateManifest
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val isChecking: Boolean = false,
    val availableUpdate: AppUpdateManifest? = null,
    val recentReleaseVersionName: String? = null,
    val recentReleaseNotes: String? = null,
    val isSheetVisible: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val readyApkFile: File? = null,
    val errorMessage: String? = null
)

@Stable
class AppUpdateController(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: GitHubReleaseUpdateRepository = GitHubReleaseUpdateRepository()
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("verve_update_prefs", Context.MODE_PRIVATE)
    private val updatesDir = File(appContext.cacheDir, "updates")

    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            recentReleaseVersionName = prefs.getString(KEY_RECENT_RELEASE_VERSION_NAME, null),
            recentReleaseNotes = prefs.getString(KEY_RECENT_RELEASE_NOTES, null)
        )
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        cleanupCompletedOrStaleUpdates()
    }

    fun cleanupCompletedOrStaleUpdates() {
        scope.launch(Dispatchers.IO) {
            val pendingVersionCode = prefs.getInt(KEY_PENDING_INSTALL_VERSION_CODE, -1)
            val isInstalledOrOutdated =
                pendingVersionCode <= 0 || BuildConfig.VERSION_CODE >= pendingVersionCode

            if (isInstalledOrOutdated) {
                deleteUpdatesDirectory()
                prefs.edit().remove(KEY_PENDING_INSTALL_VERSION_CODE).apply()
                UpdateNotificationHelper.cancelUpdateNotification(appContext)
            } else {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".part")) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun deleteUpdatesDirectory() {
        if (updatesDir.exists()) {
            updatesDir.listFiles()?.forEach { it.delete() }
            updatesDir.delete()
        }
    }

    private fun persistRecentReleaseNotes(versionName: String?, notes: String?) {
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val cleanVersion = versionName?.trim()?.takeIf { it.isNotEmpty() }

        prefs.edit()
            .putString(KEY_RECENT_RELEASE_VERSION_NAME, cleanVersion)
            .putString(KEY_RECENT_RELEASE_NOTES, cleanNotes)
            .apply()

        _uiState.update {
            it.copy(
                recentReleaseVersionName = cleanVersion ?: it.recentReleaseVersionName,
                recentReleaseNotes = cleanNotes
            )
        }
    }

    /**
     * Checks GitHub Releases whenever the app is opened and opens the modal sheet
     * automatically if a newer version is available.
     */
    fun checkForUpdatesOnAppOpen() {
        checkForUpdates(userInitiated = false, showModalIfFound = true)
    }

    fun openModalSheet() {
        if (_uiState.value.availableUpdate != null) {
            _uiState.update { it.copy(isSheetVisible = true) }
        } else {
            checkForUpdates(userInitiated = true, showModalIfFound = true)
        }
    }

    fun hideSheet() {
        _uiState.update {
            it.copy(isSheetVisible = false)
        }
    }

    fun checkForUpdates(
        userInitiated: Boolean = true,
        showModalIfFound: Boolean = userInitiated
    ) {
        if (_uiState.value.isChecking || _uiState.value.isDownloading) return

        scope.launch {
            _uiState.update { it.copy(isChecking = true, errorMessage = null) }

            val result = repository.fetchReleaseStatus()

            result.fold(
                onSuccess = { status ->
                    val manifest = status.availableUpdate
                    if (manifest == null) {
                        deleteUpdatesDirectory()
                        prefs.edit().remove(KEY_PENDING_INSTALL_VERSION_CODE).apply()
                        UpdateNotificationHelper.cancelUpdateNotification(appContext)
                    } else {
                        UpdateNotificationHelper.showUpdateAvailableNotification(
                            context = appContext,
                            manifest = manifest
                        )
                    }

                    persistRecentReleaseNotes(
                        versionName = manifest?.versionName ?: status.latestVersionName,
                        notes = manifest?.releaseNotes ?: status.latestReleaseNotes
                    )

                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = manifest,
                            isSheetVisible = manifest != null && showModalIfFound,
                            readyApkFile = null
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            errorMessage = if (userInitiated) {
                                throwable.message ?: "Unable to check for updates"
                            } else {
                                null
                            }
                        )
                    }
                }
            )
        }
    }

    fun startDownloadAndInstall() {
        val manifest = _uiState.value.availableUpdate ?: return
        val existingReady = _uiState.value.readyApkFile
        if (existingReady != null && existingReady.exists()) {
            launchInstallerSafely(existingReady, manifest.versionCode)
            return
        }
        if (_uiState.value.isDownloading) return

        downloadJob = scope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = manifest.apkSizeBytes,
                    errorMessage = null
                )
            }

            val result = repository.downloadAndVerifyApk(
                manifest = manifest,
                updatesDir = updatesDir
            ) { downloaded, total ->
                val fraction = if (total > 0L) {
                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                _uiState.update {
                    it.copy(
                        downloadProgress = fraction,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                }
            }

            result.fold(
                onSuccess = { apkFile ->
                    persistRecentReleaseNotes(manifest.versionName, manifest.releaseNotes)
                    prefs.edit()
                        .putInt(KEY_PENDING_INSTALL_VERSION_CODE, manifest.versionCode)
                        .apply()

                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            readyApkFile = apkFile
                        )
                    }

                    launchInstallerSafely(apkFile, manifest.versionCode)
                },
                onFailure = { error ->
                    deleteUpdatesDirectory()
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 0f,
                            readyApkFile = null,
                            errorMessage = error.message ?: "Update download failed"
                        )
                    }
                }
            )
        }
    }

    private fun launchInstallerSafely(apkFile: File, targetVersionCode: Int) {
        prefs.edit().putInt(KEY_PENDING_INSTALL_VERSION_CODE, targetVersionCode).apply()
        ApkInstaller.installApk(appContext, apkFile).onFailure { error ->
            _uiState.update {
                it.copy(
                    errorMessage = error.message ?: "Could not launch package installer"
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        deleteUpdatesDirectory()
        prefs.edit().remove(KEY_PENDING_INSTALL_VERSION_CODE).apply()
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                readyApkFile = null
            )
        }
    }

    fun dismissSheet() {
        cancelDownload()
        _uiState.update {
            it.copy(isSheetVisible = false)
        }
    }

    companion object {
        private const val KEY_PENDING_INSTALL_VERSION_CODE = "pending_install_version_code"
        private const val KEY_RECENT_RELEASE_VERSION_NAME = "recent_release_version_name"
        private const val KEY_RECENT_RELEASE_NOTES = "recent_release_notes"
    }
}