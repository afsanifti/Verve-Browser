package com.verve.browser.presentation.update

import android.content.Context
import androidx.compose.runtime.Stable
import com.verve.browser.BuildConfig
import com.verve.browser.data.update.ApkInstaller
import com.verve.browser.data.update.GitHubReleaseUpdateRepository
import com.verve.browser.domain.model.AppUpdateManifest
import java.io.File
import kotlinx.coroutines.CoroutineScope
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

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    fun checkForUpdatesIfDue(minIntervalMs: Long = 6L * 60L * 60L * 1000L) {
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (now - lastChecked < minIntervalMs) return
        checkForUpdates(userInitiated = false)
    }

    fun checkForUpdates(userInitiated: Boolean = true) {
        if (_uiState.value.isChecking || _uiState.value.isDownloading) return

        scope.launch {
            _uiState.update { it.copy(isChecking = true, errorMessage = null) }

            val result = repository.fetchAvailableUpdate()
            prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()

            result.fold(
                onSuccess = { manifest ->
                    val snoozedCode = prefs.getInt(KEY_SNOOZED_VERSION_CODE, -1)
                    val shouldShow = manifest != null &&
                            (userInitiated || manifest.versionCode != snoozedCode)
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            availableUpdate = manifest,
                            isSheetVisible = shouldShow,
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
            ApkInstaller.installApk(appContext, existingReady)
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
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            readyApkFile = apkFile
                        )
                    }
                    ApkInstaller.installApk(appContext, apkFile)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            errorMessage = error.message ?: "Update download failed"
                        )
                    }
                }
            )
        }
    }

    fun dismissSheet(snoozeVersion: Boolean = false) {
        downloadJob?.cancel()
        downloadJob = null
        val versionCode = _uiState.value.availableUpdate?.versionCode
        if (snoozeVersion && versionCode != null) {
            prefs.edit().putInt(KEY_SNOOZED_VERSION_CODE, versionCode).apply()
        }
        _uiState.update {
            it.copy(
                isSheetVisible = false,
                isDownloading = false
            )
        }
    }

    companion object {
        private const val KEY_LAST_CHECK_MS = "last_check_ms"
        private const val KEY_SNOOZED_VERSION_CODE = "snoozed_version_code"
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = 0f
            )
        }
    }
}

