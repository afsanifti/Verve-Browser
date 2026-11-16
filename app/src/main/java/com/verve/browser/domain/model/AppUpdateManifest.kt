package com.verve.browser.domain.model


data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkAssetName: String,
    val sha256: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long = 0L
)