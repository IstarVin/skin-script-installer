package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
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
    private val _progress = MutableStateFlow<InstallProgress?>(null)
    val progress: StateFlow<InstallProgress?> = _progress.asStateFlow()

    suspend fun execute(
        scriptId: Long,
        userId: Int = 0,
        fileConflictChoices: Map<String, FileConflictChoice> = emptyMap()
    ): Result<Installation> = withContext(Dispatchers.IO) {
        try {
            val script = repository.getScriptById(scriptId)
                ?: return@withContext Result.failure(Exception("Script not found"))

            val service = shizukuManager.fileService.value
                ?: return@withContext Result.failure(Exception("Shizuku file service not available"))

            val installPlan = buildScriptInstallPlan(script.storagePath, userId)
                .getOrElse { error -> return@withContext Result.failure(error) }

            val filesToInstall = installPlan.files.filter { plannedFile ->
                fileConflictChoices[plannedFile.destPath] != FileConflictChoice.KEEP_CURRENT
            }

            if (filesToInstall.isEmpty()) {
                return@withContext Result.failure(Exception("No files selected to install"))
            }

            // Create installation record
            val installation = Installation(
                scriptId = scriptId,
                userId = userId,
                status = InstallationStatus.INSTALLED
            )
            val installationId = repository.insertInstallation(installation)

            // Backup directory for this installation
            val backupDir = File(context.filesDir, "backups/$installationId")

            val installedFiles = mutableListOf<InstalledFile>()
            val supersededConflicts = mutableListOf<Long>()
            val affectedInstallations = linkedSetOf<Long>()

            val activeConflictsByDestPath = repository.getActiveFileOwnershipConflicts(
                destPaths = filesToInstall.map { it.destPath },
                userId = userId
            ).associateBy { conflict -> conflict.destPath }

            filesToInstall.forEachIndexed { index, plannedFile ->
                val relPath = plannedFile.relativePath
                val destPath = plannedFile.destPath

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
                    plannedFile.sourceFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val writeSuccess = service.writeFile(sourcePfd, destPath)

                if (!writeSuccess) {
                    // Continue anyway but log
                    android.util.Log.e(
                        "InstallScript",
                        "Failed to write: $relPath"
                    )
                    return@forEachIndexed
                }

                installedFiles.add(
                    InstalledFile(
                        installationId = installationId,
                        destPath = destPath,
                        wasOverwrite = wasOverwrite,
                        backupPath = backupPath,
                        supersededByInstallationId = null
                    )
                )

                activeConflictsByDestPath[destPath]?.let { conflict ->
                    supersededConflicts += conflict.installedFileId
                    affectedInstallations += conflict.installationId
                }
            }

            if (installedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to install any files"))
            }

            // Batch insert all installed file records
            repository.insertInstalledFiles(installedFiles)

            if (supersededConflicts.isNotEmpty()) {
                repository.markInstalledFilesSuperseded(
                    fileIds = supersededConflicts,
                    supersededByInstallationId = installationId
                )

                affectedInstallations.forEach { affectedInstallationId ->
                    val affectedInstallation = repository.getInstallationById(affectedInstallationId)
                        ?: return@forEach
                    if (repository.countActiveInstalledFiles(affectedInstallationId) == 0) {
                        repository.updateInstallation(
                            affectedInstallation.copy(status = InstallationStatus.SUPERSEDED)
                        )
                    }
                }
            }

            _progress.value = InstallProgress(
                currentIndex = filesToInstall.size,
                total = filesToInstall.size,
                currentFileName = "",
                isComplete = true
            )

            val completedInstallation = installation.copy(
                id = installationId,
                status = InstallationStatus.INSTALLED
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
}

