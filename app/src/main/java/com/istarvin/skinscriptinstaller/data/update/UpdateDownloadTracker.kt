package com.istarvin.skinscriptinstaller.data.update

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class UpdateDownloadTracker @Inject constructor() {

    private val _status = MutableStateFlow<UpdateDownloadStatus>(UpdateDownloadStatus.Idle)
    val status: StateFlow<UpdateDownloadStatus> = _status.asStateFlow()

    fun markDownloading(version: String, bytesDownloaded: Long, totalBytes: Long?) {
        _status.value = UpdateDownloadStatus.Running(
            version = version,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes
        )
    }

    fun markCompleted(version: String) {
        _status.value = UpdateDownloadStatus.Completed(version)
    }

    fun markFailed(version: String, message: String) {
        _status.value = UpdateDownloadStatus.Failed(version = version, message = message)
    }

    fun clear() {
        _status.value = UpdateDownloadStatus.Idle
    }
}