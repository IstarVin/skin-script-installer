package com.istarvin.skinscriptinstaller.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.istarvin.skinscriptinstaller.MainActivity
import com.istarvin.skinscriptinstaller.update.UpdateInstallerActivity

object UpdateNotifications {

    const val ChannelId = "update_downloads"
    const val NotificationId = 2001

    private const val OpenAppRequestCode = 3010

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ChannelId,
            "App updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows update download progress and install readiness"
        }
        manager.createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        context: Context,
        version: String,
        bytesDownloaded: Long,
        totalBytes: Long?
    ): Notification {
        val builder = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading update")
            .setContentText(progressText(context, version, bytesDownloaded, totalBytes))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent(context))

        if (totalBytes != null && totalBytes > 0L) {
            val progress = ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun buildCompletedNotification(
        context: Context,
        version: String,
        apkUri: Uri
    ): Notification {
        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update ready")
            .setContentText("Version $version finished downloading. Tap to install.")
            .setAutoCancel(true)
            .setContentIntent(installerPendingIntent(context, apkUri))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun buildFailedNotification(
        context: Context,
        version: String,
        message: String
    ): Notification {
        return NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Update download failed")
            .setContentText("Version $version could not be downloaded. $message")
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun progressText(
        context: Context,
        version: String,
        bytesDownloaded: Long,
        totalBytes: Long?
    ): String {
        val downloadedText = Formatter.formatShortFileSize(context, bytesDownloaded)
        val totalText = totalBytes?.let { Formatter.formatShortFileSize(context, it) }
        val percentText = totalBytes
            ?.takeIf { it > 0L }
            ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
            ?.let { "$it%" }

        return when {
            totalText != null && percentText != null -> {
                "Version $version • $percentText • $downloadedText of $totalText"
            }

            else -> "Version $version • Downloaded $downloadedText"
        }
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            OpenAppRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun installerPendingIntent(context: Context, apkUri: Uri): PendingIntent {
        val intent = UpdateInstallerActivity.createIntent(context, apkUri)
        return PendingIntent.getActivity(
            context,
            apkUri.toString().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}