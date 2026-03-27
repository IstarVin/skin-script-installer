package com.istarvin.skinscriptinstaller.data.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

    suspend fun downloadUpdate(apkUrl: String, version: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply {
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

                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
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
