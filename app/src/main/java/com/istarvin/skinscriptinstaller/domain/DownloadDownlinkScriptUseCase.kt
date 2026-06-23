package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.downlink.DownlinkRepositoryEntry
import com.istarvin.skinscriptinstaller.data.downlink.SFileDirectDownloadResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

class DownloadDownlinkScriptUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val resolver: SFileDirectDownloadResolver,
    private val importScriptUseCase: ImportScriptUseCase,
    private val classifyScriptUseCase: ClassifyScriptUseCase
) {
    suspend fun execute(entry: DownlinkRepositoryEntry): Result<SkinScript> = withContext(Dispatchers.IO) {
        val directUrl = resolver.resolve(entry.sfileUrl).getOrElse {
            return@withContext Result.failure(it)
        }
        val zipFile = createDownloadFile(entry)

        try {
            downloadZip(directUrl, zipFile)
            val zipUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )
            val script = importScriptUseCase.executeZip(zipUri).getOrElse {
                return@withContext Result.failure(it)
            }
            classifyScriptUseCase.execute(
                scriptId = script.id,
                heroName = entry.heroName,
                originalSkinName = entry.originalSkinName,
                replacementSkinName = entry.replacementSkinName
            ).getOrElse {
                return@withContext Result.failure(it)
            }
            Result.success(script)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            zipFile.delete()
        }
    }

    private fun downloadZip(url: String, destination: File) {
        destination.parentFile?.mkdirs()
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Script download failed: HTTP ${response.code}")
            }
            response.body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun createDownloadFile(entry: DownlinkRepositoryEntry): File {
        val fileName = "${entry.heroName} ${entry.originalSkinName} - ${entry.replacementSkinName}"
            .toSafeFileName()
            .ifBlank { "downlink_script" }
        var candidate = File(context.cacheDir, "$fileName.zip")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(context.cacheDir, "$fileName ($suffix).zip")
            suffix++
        }
        return candidate
    }

    private fun String.toSafeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
