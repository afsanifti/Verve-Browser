package com.verve.browser.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.FileOutputStream
import java.nio.channels.FileChannel

internal data class MediaStoreDownloadEntry(
    val displayName: String,
    val contentUri: Uri?
)

internal class WritableDownloadSink(
    val channel: FileChannel,
    private val outputStream: FileOutputStream,
    private val parcelFileDescriptor: ParcelFileDescriptor
) {
    fun close() {
        runCatching { channel.force(false) }
        runCatching { channel.close() }
        runCatching { outputStream.flush() }
        runCatching { outputStream.close() }
        runCatching { parcelFileDescriptor.close() }
    }
}

internal object DownloadMediaStoreHelper {

    fun insertDownload(
        context: Context,
        requestedName: String,
        mimeType: String
    ): MediaStoreDownloadEntry {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, requestedName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return MediaStoreDownloadEntry(displayName = requestedName, contentUri = null)

        val actualDisplayName = queryDisplayName(context, uri) ?: requestedName
        return MediaStoreDownloadEntry(
            displayName = actualDisplayName,
            contentUri = uri
        )
    }

    fun updateMetadata(
        context: Context,
        contentUri: Uri,
        newDisplayName: String,
        newMimeType: String
    ): String {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, newDisplayName)
                put(MediaStore.Downloads.MIME_TYPE, newMimeType)
            }
            context.contentResolver.update(contentUri, values, null, null)
        }
        return queryDisplayName(context, contentUri) ?: newDisplayName
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    fun openWritableSink(
        context: Context,
        contentUri: Uri,
        append: Boolean,
        offsetBytes: Long
    ): WritableDownloadSink {
        val pfd = context.contentResolver.openFileDescriptor(contentUri, "rw")
            ?: error("Unable to open MediaStore file descriptor for $contentUri")
        val fos = FileOutputStream(pfd.fileDescriptor)
        val channel = fos.channel
        if (append && offsetBytes > 0L) {
            channel.position(offsetBytes)
        } else {
            channel.truncate(0L)
            channel.position(0L)
        }
        return WritableDownloadSink(
            channel = channel,
            outputStream = fos,
            parcelFileDescriptor = pfd
        )
    }

    fun finalizeDownload(
        context: Context,
        contentUri: Uri,
        finalSizeBytes: Long
    ) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
                if (finalSizeBytes > 0L) {
                    put(MediaStore.Downloads.SIZE, finalSizeBytes)
                }
            }
            context.contentResolver.update(contentUri, values, null, null)
        }
    }

    fun getCurrentDownloadedSize(context: Context, contentUri: Uri): Long {
        return runCatching {
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                pfd.statSize.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)
    }

    fun deleteEntry(context: Context, contentUri: Uri) {
        runCatching { context.contentResolver.delete(contentUri, null, null) }
    }
}