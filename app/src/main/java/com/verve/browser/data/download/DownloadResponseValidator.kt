package com.verve.browser.data.download

import android.webkit.MimeTypeMap
import android.webkit.URLUtil

internal const val HEADER_SAMPLE_LIMIT_BYTES = 512

internal object DownloadResponseValidator {

    private val BINARY_EXTENSIONS = setOf("zip", "rar", "7z", "apk", "xlsx", "xls", "docx", "doc")

    fun validateExpectedTypeVsResponseMime(
        expectedFileName: String,
        expectedMimeType: String?,
        responseMimeType: String?
    ): String? {
        val respMime = responseMimeType.orEmpty().lowercase()
        if (!respMime.contains("text/html") && !respMime.contains("application/xhtml+xml")) {
            return null
        }

        val expMime = expectedMimeType.orEmpty().lowercase()
        val expectsPdf = expectedFileName.endsWith(".pdf", ignoreCase = true) ||
                expMime.contains("application/pdf")
        if (expectsPdf) {
            return "Server returned an HTML page instead of a PDF"
        }

        val ext = expectedFileName.substringAfterLast('.', "").lowercase()
        if (ext in BINARY_EXTENSIONS) {
            return "Server returned an HTML page instead of the requested file"
        }
        return null
    }

    fun validateResponsePayload(
        statusCode: Int,
        expectedFileName: String,
        responseMimeType: String?,
        leadingBytes: ByteArray,
        totalBytesWritten: Long
    ): String? {
        if (statusCode !in 200..299) {
            return "Server returned HTTP $statusCode"
        }
        if (totalBytesWritten <= 0L || leadingBytes.isEmpty()) {
            return "Empty file received from server"
        }

        val mimeLower = responseMimeType.orEmpty().lowercase()
        val headText = leadingBytes.decodeToString().trimStart().lowercase()
        val looksLikeHtml = mimeLower.contains("text/html") ||
                mimeLower.contains("application/xhtml+xml") ||
                headText.startsWith("<!doctype") ||
                headText.startsWith("<html") ||
                headText.startsWith("<head") ||
                headText.startsWith("<body") ||
                headText.startsWith("<script")

        val expectsPdf = expectedFileName.endsWith(".pdf", ignoreCase = true) ||
                mimeLower.contains("application/pdf")

        if (expectsPdf && !hasPdfMagicHeader(leadingBytes)) {
            return when {
                headText.contains("csrf") ->
                    "CSRF validation failed — reload the page and try again"
                headText.contains("session") || headText.contains("expired") || headText.contains("login") ->
                    "Session expired — please sign in again"
                looksLikeHtml ->
                    "Server returned an HTML error page instead of a PDF"
                else ->
                    "Invalid PDF file signature"
            }
        }

        val ext = expectedFileName.substringAfterLast('.', "").lowercase()
        if (ext in BINARY_EXTENSIONS && looksLikeHtml) {
            return when {
                headText.contains("csrf") ->
                    "CSRF validation failed — reload the page and try again"
                headText.contains("session") || headText.contains("expired") || headText.contains("login") ->
                    "Session expired — please sign in again"
                else ->
                    "Server returned an HTML error page instead of the requested file"
            }
        }

        return null
    }

    fun resolveFinalFileName(
        fallbackName: String,
        finalUrl: String,
        responseDisposition: String?,
        responseMimeType: String?
    ): String {
        val guessed = if (!responseDisposition.isNullOrBlank()) {
            URLUtil.guessFileName(finalUrl, responseDisposition, responseMimeType)
        } else {
            fallbackName
        }
        return sanitizeFileName(guessed.ifBlank { fallbackName })
    }

    fun sanitizeFileName(rawName: String): String {
        val stripped = rawName.replace(
            Regex("\\.(php|aspx|jsp|cgi|do)\\.(pdf|zip|csv|xlsx|xls|doc|docx|txt)$", RegexOption.IGNORE_CASE),
            ".$2"
        )
        return stripped.ifBlank { "download" }
    }

    fun resolveMimeType(fileName: String, serverMimeType: String?): String {
        val cleanedServerMime = serverMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val isGenericMime = cleanedServerMime.isBlank() ||
                cleanedServerMime == "*/*" ||
                cleanedServerMime == "application/octet-stream" ||
                cleanedServerMime == "binary/octet-stream"

        return if (isGenericMime) {
            guessMimeTypeFromName(fileName)
        } else {
            cleanedServerMime
        }
    }

    fun guessMimeTypeFromName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "apk") return "application/vnd.android.package-archive"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun hasPdfMagicHeader(bytes: ByteArray): Boolean {
        val scanLimit = minOf(bytes.size, 1024)
        for (i in 0..scanLimit - 5) {
            if (bytes[i] == 0x25.toByte() &&
                bytes[i + 1] == 0x50.toByte() &&
                bytes[i + 2] == 0x44.toByte() &&
                bytes[i + 3] == 0x46.toByte() &&
                bytes[i + 4] == 0x2D.toByte()
            ) {
                return true
            }
        }
        return false
    }
}