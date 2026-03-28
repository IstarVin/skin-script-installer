package com.istarvin.skinscriptinstaller.data.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateDownloadService : Service() {

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var updateDownloadTracker: UpdateDownloadTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkUrl = intent?.getStringExtra(ExtraApkUrl)
        val version = intent?.getStringExtra(ExtraVersion)

        if (apkUrl.isNullOrBlank() || version.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (activeJob?.isActive == true) {
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            UpdateNotifications.NotificationId,
            UpdateNotifications.buildProgressNotification(
                context = this,
                version = version,
                bytesDownloaded = 0L,
                totalBytes = null
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        activeJob = serviceScope.launch {
            var lastNotificationAt = 0L
            var lastProgressPercent = -1
            var lastDownloadedBytes = -1L

            val result = appUpdateManager.downloadUpdate(
                apkUrl = apkUrl,
                version = version
            ) { progress ->
                updateDownloadTracker.markDownloading(
                    version = progress.version,
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes
                )

                val now = SystemClock.elapsedRealtime()
                val shouldNotify = when {
                    progress.totalBytes != null -> {
                        val percent = progress.progressPercent ?: 0
                        val changed = percent != lastProgressPercent
                        if (changed) {
                            lastProgressPercent = percent
                        }
                        changed
                    }

                    else -> {
                        val enoughTimePassed = now - lastNotificationAt >= 750L
                        val enoughBytesPassed =
                            lastDownloadedBytes < 0L || progress.bytesDownloaded - lastDownloadedBytes >= 256 * 1024L
                        enoughTimePassed && enoughBytesPassed
                    }
                }

                if (shouldNotify) {
                    lastNotificationAt = now
                    lastDownloadedBytes = progress.bytesDownloaded
                    NotificationManagerCompat.from(this@UpdateDownloadService).notify(
                        UpdateNotifications.NotificationId,
                        UpdateNotifications.buildProgressNotification(
                            context = this@UpdateDownloadService,
                            version = progress.version,
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes
                        )
                    )
                }
            }

            val terminalNotification = result.fold(
                onSuccess = { apkUri ->
                    updateDownloadTracker.markCompleted(version)
                    UpdateNotifications.buildCompletedNotification(
                        context = this@UpdateDownloadService,
                        version = version,
                        apkUri = apkUri
                    )
                },
                onFailure = { error ->
                    val message = error.message ?: "Download failed"
                    updateDownloadTracker.markFailed(version, message)
                    UpdateNotifications.buildFailedNotification(
                        context = this@UpdateDownloadService,
                        version = version,
                        message = message
                    )
                }
            )

            stopForeground(STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this@UpdateDownloadService).notify(
                UpdateNotifications.NotificationId,
                terminalNotification
            )
            stopSelf(startId)
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        if (activeJob?.isActive == true) {
            val version = (updateDownloadTracker.status.value as? UpdateDownloadStatus.Running)?.version
            if (version != null) {
                updateDownloadTracker.markFailed(version, "Update download was interrupted")
            }
        }
        activeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ExtraApkUrl = "extra_apk_url"
        private const val ExtraVersion = "extra_version"

        fun createIntent(context: Context, apkUrl: String, version: String): Intent {
            return Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(ExtraApkUrl, apkUrl)
                putExtra(ExtraVersion, version)
            }
        }
    }
}