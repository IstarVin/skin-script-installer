package com.istarvin.skinscriptinstaller.data.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun downloadUpdate(
        apkUrl: String,
        version: String,
        onProgress: ((UpdateDownloadProgress) -> Unit)? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.filesDir, "updates").apply {
                mkdirs()
            }
            updatesDir.listFiles()?.forEach(File::delete)

            val targetFile = File(
                updatesDir,
                "skinscriptinstaller-${sanitizeVersion(version)}.apk"
            )

            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "SkinScriptInstaller/${sanitizeVersion(version)}")
            }

            try {
                if (connection.responseCode !in HttpURLConnection.HTTP_OK..299) {
                    throw IOException("Download failed with HTTP ${connection.responseCode}")
                }

                val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                var downloadedBytes = 0L
                onProgress?.invoke(
                    UpdateDownloadProgress(
                        version = version,
                        bytesDownloaded = downloadedBytes,
                        totalBytes = totalBytes
                    )
                )

                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) {
                                break
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            onProgress?.invoke(
                                UpdateDownloadProgress(
                                    version = version,
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                        output.flush()
                    }
                }

                if (downloadedBytes <= 0L) {
                    throw IOException("Downloaded update file is empty")
                }

                if (totalBytes != null && downloadedBytes != totalBytes) {
                    throw IOException(
                        "Download incomplete: expected $totalBytes bytes but received $downloadedBytes"
                    )
                }
            } catch (error: Throwable) {
                targetFile.delete()
                throw error
            } finally {
                connection.disconnect()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
        }
    }

    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun sanitizeVersion(version: String): String {
        return version.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
