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

            val latestReleaseApi = "https://api.github.com/repos/$githubRepository/releases/latest"
            val apiConnection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "VerveBrowser/${BuildConfig.VERSION_NAME}")
            }

            val releaseJson = try {
                val code = apiConnection.responseCode
                if (code == 404) {
                    return@runCatching ReleaseCheckResult(
                        availableUpdate = null,
                        latestVersionName = null,
                        latestReleaseNotes = null
                    )
                }
                check(code in 200..299) { "GitHub Releases API returned HTTP $code" }
                JSONObject(apiConnection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                apiConnection.disconnect()
            }

            if (releaseJson.optBoolean("draft", false)) {
                return@runCatching ReleaseCheckResult(
                    availableUpdate = null,
                    latestVersionName = null,
                    latestReleaseNotes = null
                )
            }

            val assets = releaseJson.optJSONArray("assets")
                ?: return@runCatching ReleaseCheckResult(
                    availableUpdate = null,
                    latestVersionName = null,
                    latestReleaseNotes = releaseJson.optString("body").trim().ifBlank { null }
                )

            var manifestUrl: String? = null
            val assetUrlByName = HashMap<String, String>()
            val assetSizeByName = HashMap<String, Long>()

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.isNotBlank() && url.isNotBlank()) {
                    assetUrlByName[name] = url
                    assetSizeByName[name] = asset.optLong("size", 0L)
                    if (name == "update.json") {
                        manifestUrl = url
                    }
                }
            }

            val resolvedManifestUrl = manifestUrl
                ?: return@runCatching ReleaseCheckResult(
                    availableUpdate = null,
                    latestVersionName = releaseJson.optString("tag_name").removePrefix("v").trim().ifBlank { null },
                    latestReleaseNotes = releaseJson.optString("body").trim().ifBlank { null }
                )

            val manifestBody = fetchText(resolvedManifestUrl)
            val json = JSONObject(manifestBody)

            val remoteVersionCode = json.getInt("versionCode")
            val remoteVersionName = json.getString("versionName").trim()
            val apkAssetName = json.getString("apkAssetName").trim()
            val sha256 = json.getString("sha256").trim()
            val githubReleaseBody = releaseJson.optString("body").trim()
            val manifestReleaseNotes = json.optString("releaseNotes").trim()
            val releaseNotes = githubReleaseBody.ifBlank { manifestReleaseNotes }

            check(remoteVersionCode > 0 && remoteVersionName.isNotEmpty() && sha256.length == 64) {
                "Invalid update.json manifest fields"
            }

            val manifest = if (remoteVersionCode > currentVersionCode) {
                val apkDownloadUrl = assetUrlByName[apkAssetName]
                    ?: error("APK asset '$apkAssetName' listed in update.json was not found in release assets")

                AppUpdateManifest(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    apkAssetName = apkAssetName,
                    sha256 = sha256,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkDownloadUrl,
                    apkSizeBytes = assetSizeByName[apkAssetName] ?: 0L
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

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "VerveBrowser/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = conn.responseCode
            check(code in 200..299) { "Failed to download manifest (HTTP $code)" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}