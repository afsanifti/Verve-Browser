package com.verve.browser.presentation.downloads

import java.util.Locale

enum class DownloadStatus {
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

enum class DownloadFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In progress")
}

enum class DownloadFileType {
    PDF,
    ARCHIVE,
    DOCUMENT,
    IMAGE,
    OTHER
}

data class DownloadItem(
    val id: String,
    val fileName: String,
    val domain: String,
    val url: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val fileType: DownloadFileType,
    val dateGroup: String,
    val remainingSeconds: Int? = null,
    val mimeType: String = "*/*",
    val negotiatedProtocol: String? = null,
    val contentUri: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val isInProgress: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED

    val isHttp3: Boolean
        get() = negotiatedProtocol?.startsWith("h3", ignoreCase = true) == true
}

data class DownloadDateGroup(
    val title: String,
    val items: List<DownloadItem>
)

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

internal fun formatActiveProgressSubtitle(item: DownloadItem): String {
    val downloadedText = formatFileSize(item.downloadedBytes)
    val sizeText = if (item.totalBytes > 0L) {
        val totalMb = item.totalBytes / (1024.0 * 1024.0)
        val downloadedMb = item.downloadedBytes / (1024.0 * 1024.0)
        if (totalMb >= 1.0) {
            String.format(Locale.US, "%.0f of %.0f MB", downloadedMb, totalMb)
        } else {
            "$downloadedText of ${formatFileSize(item.totalBytes)}"
        }
    } else {
        downloadedText
    }

    val etaText = when {
        item.status == DownloadStatus.PAUSED -> "Paused"
        item.remainingSeconds != null && item.remainingSeconds > 0 -> {
            if (item.remainingSeconds >= 60) {
                "${item.remainingSeconds / 60} min left"
            } else {
                "${item.remainingSeconds} seconds left"
            }
        }
        else -> null
    }

    val protocolTag = if (item.isHttp3) "HTTP/3" else null

    return listOfNotNull(sizeText, etaText, protocolTag).joinToString(" · ")
}

internal fun inferDownloadFileType(fileName: String, mimeType: String?): DownloadFileType {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    val mime = mimeType?.lowercase(Locale.US).orEmpty()
    return when {
        ext == "pdf" || mime == "application/pdf" -> DownloadFileType.PDF
        ext in setOf("zip", "rar", "7z", "tar", "gz", "apk") ||
                mime.contains("zip") || mime.contains("archive") ||
                mime == "application/vnd.android.package-archive" -> DownloadFileType.ARCHIVE
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "svg") ||
                mime.startsWith("image/") -> DownloadFileType.IMAGE
        ext in setOf("txt", "doc", "docx", "md", "csv", "json", "xml") ||
                mime.startsWith("text/") -> DownloadFileType.DOCUMENT
        else -> DownloadFileType.OTHER
    }
}