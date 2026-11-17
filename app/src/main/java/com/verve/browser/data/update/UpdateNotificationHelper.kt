package com.verve.browser.data.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.verve.browser.MainActivity
import com.verve.browser.R
import com.verve.browser.domain.model.AppUpdateManifest

object UpdateNotificationHelper {

    const val CHANNEL_ID = "verve_app_updates"
    const val EXTRA_OPEN_UPDATE_SHEET = "com.verve.browser.EXTRA_OPEN_UPDATE_SHEET"
    private const val NOTIFICATION_ID = 4091
    private const val PREFS_NAME = "verve_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION_CODE = "last_notified_version_code"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new version of Verve Browser is available"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun showUpdateAvailableNotification(
        context: Context,
        manifest: AppUpdateManifest,
        forceNotify: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedCode = prefs.getInt(KEY_LAST_NOTIFIED_VERSION_CODE, -1)
        if (!forceNotify && lastNotifiedCode == manifest.versionCode) {
            return
        }

        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_UPDATE_SHEET, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            manifest.versionCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = manifest.releaseNotes
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "Version ${manifest.versionName} is ready to download and install."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Verve ${manifest.versionName} is available")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_VERSION_CODE, manifest.versionCode).apply()
        } catch (_: SecurityException) {
            // Permission was revoked while posting the notification
        }
    }

    fun cancelUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}