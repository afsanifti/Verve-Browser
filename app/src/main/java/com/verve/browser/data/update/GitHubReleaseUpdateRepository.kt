package com.verve.browser.data.update

import com.verve.browser.BuildConfig
import com.verve.browser.domain.model.AppUpdateManifest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ReleaseCheckResult(
    val availableUpdate: AppUpdateManifest?,
    val latestVersionName: String?,
    val latestReleaseNotes: String?
)

class GitHubReleaseUpdateRepository(
    private val githubRepository: String = BuildConfig.GITHUB_REPOSITORY
) {

    suspend fun fetchAvailableUpdate(
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): Result<AppUpdateManifest?> = fetchReleaseStatus(currentVersionCode).map { it.availableUpdate }

    suspend fun fetchReleaseStatus(
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): Result<ReleaseCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(githubRepository.contains("/") && githubRepository != "OWNER/REPO") {
                "Configure githubRepository (owner/repo) before checking for updates."
            }

            // Fetch update.json directly via GitHub's release asset redirect URL so
            // update checks never hit the 60 req/hr unauthenticated REST API rate limit.
            val latestManifestUrl =
                "https://github.com/$githubRepository/releases/latest/download/update.json"
            val manifestBody = fetchTextOrNullOnNotFound(latestManifestUrl)
                ?: return@runCatching ReleaseCheckResult(
                    availableUpdate = null,
                    latestVersionName = null,
                    latestReleaseNotes = null
                )

            val json = JSONObject(manifestBody)

            val remoteVersionCode = json.getInt("versionCode")
            val remoteVersionName = json.getString("versionName").trim()
            val apkAssetName = json.getString("apkAssetName").trim()
            val sha256 = json.getString("sha256").trim()
            val releaseNotes = json.optString("releaseNotes").trim()
            val apkSizeBytes = json.optLong("apkSizeBytes", 0L).coerceAtLeast(0L)

            check(remoteVersionCode > 0 && remoteVersionName.isNotEmpty() && sha256.length == 64) {
                "Invalid update.json manifest fields"
            }

            val manifest = if (remoteVersionCode > currentVersionCode) {
                val tagName = if (remoteVersionName.startsWith("v")) {
                    remoteVersionName
                } else {
                    "v$remoteVersionName"
                }
                val apkDownloadUrl = json.optString("apkDownloadUrl").trim().ifBlank {
                    "https://github.com/$githubRepository/releases/download/$tagName/$apkAssetName"
                }

                AppUpdateManifest(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    apkAssetName = apkAssetName,
                    sha256 = sha256,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkDownloadUrl,
                    apkSizeBytes = apkSizeBytes
                )
            } else {
                null
            }

            ReleaseCheckResult(
                availableUpdate = manifest,
                latestVersionName = remoteVersionName,
                latestReleaseNotes = releaseNotes.ifBlank { null }
            )
        }
    }

    suspend fun downloadAndVerifyApk(
        manifest: AppUpdateManifest,
        updatesDir: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            updatesDir.listFiles()?.forEach { existing ->
                if (existing.name != manifest.apkAssetName) {
                    existing.delete()
                }
            }

            val targetFile = File(updatesDir, manifest.apkAssetName)
            val tempFile = File(updatesDir, "${manifest.apkAssetName}.part")
            if (tempFile.exists()) tempFile.delete()

            val connection = (URL(manifest.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "VerveBrowser/${BuildConfig.VERSION_NAME}")
            }

            try {
                val code = connection.responseCode
                check(code in 200..299) { "APK download failed with HTTP $code" }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: manifest.apkSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            onProgress(downloaded, totalBytes)
                        }
                        output.flush()
                    }
                }

                val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computedSha256.equals(manifest.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    error("SHA-256 mismatch: expected ${manifest.sha256}, got $computedSha256")
                }

                if (targetFile.exists()) targetFile.delete()
                check(tempFile.renameTo(targetFile)) { "Failed to finalize verified APK file" }
                targetFile
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun fetchTextOrNullOnNotFound(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VerveBrowser/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = conn.responseCode
            if (code == 404) return null
            check(code in 200..299) { "Update check failed (HTTP $code)" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}