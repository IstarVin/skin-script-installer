package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
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
 * Restores the original files after a skin script installation.
 * - Files that were newly added (wasOverwrite=false) → deleted from ML path
 * - Files that were overwritten (wasOverwrite=true) → restored from backup
 */
class RestoreScriptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScriptRepository,
    private val shizukuManager: ShizukuManager
) {
    private val _progress = MutableStateFlow<InstallProgress?>(null)
    val progress: StateFlow<InstallProgress?> = _progress.asStateFlow()

    suspend fun execute(installationId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val installation = repository.getInstallationById(installationId)
                ?: return@withContext Result.failure(Exception("Installation not found"))

            if (installation.status == "restored") {
                return@withContext Result.failure(Exception("Installation already restored"))
            }

            val service = shizukuManager.fileService.value
                ?: return@withContext Result.failure(Exception("Shizuku file service not available"))

            val installedFiles = repository.getInstalledFilesByInstallation(installationId)

            if (installedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No installed files to restore"))
            }

            installedFiles.forEachIndexed { index, file ->
                _progress.value = InstallProgress(
                    currentIndex = index + 1,
                    total = installedFiles.size,
                    currentFileName = file.destPath.substringAfterLast('/')
                )

                if (!file.wasOverwrite) {
                    // File was newly added — just delete it
                    service.deleteFile(file.destPath)
                } else if (file.backupPath != null) {
                    // File was overwritten — restore from backup
                    val backupFile = File(file.backupPath)
                    if (backupFile.exists()) {
                        val pfd = ParcelFileDescriptor.open(
                            backupFile,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                        service.writeFile(pfd, file.destPath)
                        // Delete the backup file after restoring
                        backupFile.delete()
                    }
                }
            }

            // Update installation status
            repository.updateInstallation(
                installation.copy(
                    status = "restored",
                    restoredAt = System.currentTimeMillis()
                )
            )

            // Clean up the backup directory for this installation
            val backupDir = File(context.filesDir, "backups/$installationId")
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }

            _progress.value = InstallProgress(
                currentIndex = installedFiles.size,
                total = installedFiles.size,
                currentFileName = "",
                isComplete = true
            )

            Result.success(Unit)
        } catch (e: Exception) {
            _progress.value = null
            Result.failure(e)
        }
    }

    fun resetProgress() {
        _progress.value = null
    }
}

