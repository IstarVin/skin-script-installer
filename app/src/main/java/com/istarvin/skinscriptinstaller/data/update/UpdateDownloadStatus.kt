package com.istarvin.skinscriptinstaller.data.update

sealed interface UpdateDownloadStatus {
    data object Idle : UpdateDownloadStatus

    data class Running(
        val version: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?
    ) : UpdateDownloadStatus

    data class Completed(val version: String) : UpdateDownloadStatus

    data class Failed(
        val version: String,
        val message: String
    ) : UpdateDownloadStatus
}