package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Installs a skin script's files to the Mobile Legends assets directory via Shizuku.
 * Backs up any existing files that would be overwritten.
 */
class InstallScriptUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository,
    private val shizukuManager: ShizukuManager
) {
    companion object {
        private const val ML_ASSETS_REL = "Android/data/com.mobile.legends/files/dragon2017/assets"
    }

    private val _progress = MutableStateFlow<InstallProgress?>(null)
    val progress: StateFlow<InstallProgress?> = _progress.asStateFlow()

    suspend fun execute(scriptId: Long, userId: Int = 0): Result<Installation> = withContext(Dispatchers.IO) {
        try {
            val script = repository.getScriptById(scriptId)
                ?: return@withContext Result.failure(Exception("Script not found"))

            val service = shizukuManager.fileService.value
                ?: return@withContext Result.failure(Exception("Shizuku file service not available"))

            // Find the assets subtree within the imported script
            val assetsDir = File(script.storagePath, ML_ASSETS_REL)
            if (!assetsDir.exists() || !assetsDir.isDirectory) {
                return@withContext Result.failure(
                    Exception("Script has no assets directory at: ${assetsDir.path}")
                )
            }

            // Collect all files to install
            val filesToInstall = mutableListOf<File>()
            collectFiles(assetsDir, filesToInstall)

            if (filesToInstall.isEmpty()) {
                return@withContext Result.failure(Exception("No files to install"))
            }

            // Create installation record
            val installation = Installation(scriptId = scriptId)
            val installationId = repository.insertInstallation(installation)

            // Backup directory for this installation
            val backupDir = File(context.filesDir, "backups/$installationId")

            val installedFiles = mutableListOf<InstalledFile>()

            val targetAssetsRoot = buildAssetsPath(userId)

            filesToInstall.forEachIndexed { index, sourceFile ->
                // Calculate relative path from the assets dir
                val relPath = sourceFile.relativeTo(assetsDir).path
                val destPath = "$targetAssetsRoot/$relPath"

                _progress.value = InstallProgress(
                    currentIndex = index + 1,
                    total = filesToInstall.size,
                    currentFileName = relPath
                )

                // Check if destination exists — if so, back it up
                val wasOverwrite = service.exists(destPath)
                var backupPath: String? = null

                if (wasOverwrite) {
                    val backupFile = File(backupDir, relPath)
                    backupFile.parentFile?.mkdirs()
                    backupPath = backupFile.absolutePath

                    // Read from ML path via Shizuku, write to local backup
                    val pfd = service.openFileForRead(destPath)
                    ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                        backupFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Ensure parent directory exists on target
                val destParent = destPath.substringBeforeLast('/')
                service.mkdirs(destParent)

                // Write source file to ML path via Shizuku
                val sourcePfd = ParcelFileDescriptor.open(
                    sourceFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val writeSuccess = service.writeFile(sourcePfd, destPath)

                if (!writeSuccess) {
                    // Continue anyway but log
                    android.util.Log.e(
                        "InstallScript",
                        "Failed to write: $relPath"
                    )
                }

                installedFiles.add(
                    InstalledFile(
                        installationId = installationId,
                        destPath = destPath,
                        wasOverwrite = wasOverwrite,
                        backupPath = backupPath
                    )
                )
            }

            // Batch insert all installed file records
            repository.insertInstalledFiles(installedFiles)

            _progress.value = InstallProgress(
                currentIndex = filesToInstall.size,
                total = filesToInstall.size,
                currentFileName = "",
                isComplete = true
            )

            val completedInstallation = installation.copy(
                id = installationId,
                status = "installed"
            )

            Result.success(completedInstallation)
        } catch (e: Exception) {
            _progress.value = null
            Result.failure(e)
        }
    }

    fun resetProgress() {
        _progress.value = null
    }

    private fun collectFiles(dir: File, result: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectFiles(file, result)
            } else {
                result.add(file)
            }
        }
    }

    private fun buildAssetsPath(userId: Int): String {
        return "/storage/emulated/$userId/$ML_ASSETS_REL"
    }
}

