package com.verve.browser.presentation.browser.util

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import com.verve.browser.data.download.VerveDownloadManager
import java.io.File

class VervePdfBridge(
    private val context: Context,
    private val onPdfReady: (uri: Uri, title: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onBase64PdfReceived(base64Data: String?, rawFileName: String?) {
        onBase64PdfWithSourceReceived(base64Data, rawFileName, "https://localhost/")
    }

    @JavascriptInterface
    fun onBase64PdfWithSourceReceived(
        base64Data: String?,
        rawFileName: String?,
        sourceUrl: String?
    ) {
        if (base64Data.isNullOrBlank()) return

        runCatching {
            val cleanBase64 = base64Data.substringAfter("base64,", base64Data).trim()
            val rawBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val pdfHeaderOffset = findPdfHeaderOffset(rawBytes)

            if (pdfHeaderOffset < 0) {
                val preview = rawBytes.take(120).toByteArray().decodeToString()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                VerveDownloadManager.recordFailedDownload(
                    fileName = sanitizeName(rawFileName, defaultExt = "pdf"),
                    sourceUrl = sourceUrl.orEmpty().ifBlank { "https://localhost/" },
                    errorMessage = "Invalid PDF (${rawBytes.size} B): $preview",
                    isPostRequest = true
                )
                return
            }

            // Trim any leading BOM or whitespace emitted by PHP before %PDF-
            val pdfBytes = if (pdfHeaderOffset > 0) {
                rawBytes.copyOfRange(pdfHeaderOffset, rawBytes.size)
            } else {
                rawBytes
            }

            val finalName = sanitizeName(rawFileName, defaultExt = "pdf")
            val cacheDir = File(context.cacheDir, "inline_pdfs").apply { mkdirs() }
            pruneOldCacheFiles(cacheDir)

            val outputFile = File(cacheDir, "${System.currentTimeMillis()}_$finalName")
            outputFile.writeBytes(pdfBytes)

            val fileUri = Uri.fromFile(outputFile)
            mainHandler.post {
                onPdfReady(fileUri, finalName)
            }
        }
    }

    @JavascriptInterface
    fun onBase64FileReceived(
        base64Data: String?,
        rawFileName: String?,
        rawMimeType: String?,
        sourceUrl: String?
    ) {
        if (base64Data.isNullOrBlank()) return

        runCatching {
            val cleanBase64 = base64Data.substringAfter("base64,", base64Data).trim()
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (bytes.isEmpty()) return

            val safeName = sanitizeName(rawFileName, defaultExt = null)
            VerveDownloadManager.saveBridgedFileToDownloads(
                context = context,
                bytes = bytes,
                fileName = safeName,
                mimeType = rawMimeType,
                sourceUrl = sourceUrl.orEmpty().ifBlank { "https://localhost/" },
                isPostRequest = true
            )
        }
    }

    @JavascriptInterface
    fun onPostDownloadError(
        rawFileName: String?,
        sourceUrl: String?,
        httpStatus: Int,
        contentType: String?,
        bodySnippet: String?
    ) {
        val rawSnippet = bodySnippet.orEmpty()
        val explicitAlert = if (rawSnippet.startsWith("ALERT:")) {
            rawSnippet.removePrefix("ALERT:").trim()
        } else {
            null
        }
        val snippetLower = rawSnippet.lowercase()
        val reason = when {
            !explicitAlert.isNullOrBlank() -> explicitAlert
            snippetLower.contains("csrf") ->
                "CSRF validation failed — reload the page and try again"
            snippetLower.contains("session") || snippetLower.contains("login") || httpStatus == 401 || httpStatus == 403 ->
                "Session expired — please sign in again"
            httpStatus >= 400 ->
                "Server returned HTTP $httpStatus"
            contentType.orEmpty().contains("html", ignoreCase = true) ->
                "Server returned an HTML page instead of a file"
            else ->
                "Download failed"
        }
        VerveDownloadManager.recordFailedDownload(
            fileName = sanitizeName(rawFileName, defaultExt = "pdf"),
            sourceUrl = sourceUrl.orEmpty().ifBlank { "https://localhost/" },
            errorMessage = reason,
            isPostRequest = true
        )
    }

    private fun findPdfHeaderOffset(bytes: ByteArray): Int {
        val scanLimit = minOf(bytes.size, 1024)
        for (i in 0..scanLimit - 5) {
            if (bytes[i] == 0x25.toByte() &&     // %
                bytes[i + 1] == 0x50.toByte() && // P
                bytes[i + 2] == 0x44.toByte() && // D
                bytes[i + 3] == 0x46.toByte() && // F
                bytes[i + 4] == 0x2D.toByte()    // -
            ) {
                return i
            }
        }
        return -1
    }

    private fun sanitizeName(rawFileName: String?, defaultExt: String?): String {
        val sanitizedBase = (rawFileName ?: "")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { if (defaultExt != null) "document.$defaultExt" else "download" }

        return if (defaultExt != null && !sanitizedBase.endsWith(".$defaultExt", ignoreCase = true)) {
            "$sanitizedBase.$defaultExt"
        } else {
            sanitizedBase
        }
    }

    private fun pruneOldCacheFiles(dir: File, maxFiles: Int = 15) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (files.size > maxFiles) {
            files.drop(maxFiles).forEach { runCatching { it.delete() } }
        }
    }
}