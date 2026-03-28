package com.istarvin.skinscriptinstaller.data.update

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateDownloadStarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val updateDownloadTracker: UpdateDownloadTracker
) {

    fun startDownload(apkUrl: String, version: String): Result<Unit> {
        if (updateDownloadTracker.status.value is UpdateDownloadStatus.Running) {
            return Result.failure(IllegalStateException("An update download is already in progress"))
        }

        updateDownloadTracker.markDownloading(version, bytesDownloaded = 0L, totalBytes = null)

        return runCatching {
            ContextCompat.startForegroundService(
                context,
                UpdateDownloadService.createIntent(
                    context = context,
                    apkUrl = apkUrl,
                    version = version
                )
            )
            Unit
        }.onFailure { error ->
            updateDownloadTracker.markFailed(
                version = version,
                message = error.message ?: "Failed to start update download"
            )
        }
    }
}