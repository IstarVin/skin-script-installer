package com.istarvin.skinscriptinstaller.data.update

data class UpdateDownloadProgress(
    val version: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?
) {
    val progressPercent: Int?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { ((bytesDownloaded * 100) / it).toInt().coerceIn(0, 100) }
}