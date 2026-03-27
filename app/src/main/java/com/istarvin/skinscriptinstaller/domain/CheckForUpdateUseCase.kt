package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.BuildConfig
import com.istarvin.skinscriptinstaller.data.network.api.GitHubApiService
import javax.inject.Inject

data class ReleaseInfo(
    val version: String,
    val releaseNotes: String,
    val releaseUrl: String
)

class CheckForUpdateUseCase @Inject constructor(
    private val gitHubApiService: GitHubApiService
) {
    /**
     * Returns [Result.success] with a non-null [ReleaseInfo] when a newer version is available,
     * [Result.success] with null when the app is already up to date, or [Result.failure] on error.
     */
    suspend fun execute(): Result<ReleaseInfo?> = runCatching {
        val release = gitHubApiService.getLatestRelease()
        val latestVersion = release.tagName.removePrefix("v").trim()
        val currentVersion = BuildConfig.VERSION_NAME.trim()

        if (isNewerVersion(latestVersion, currentVersion)) {
            ReleaseInfo(
                version = latestVersion,
                releaseNotes = release.body,
                releaseUrl = release.htmlUrl
            )
        } else {
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
