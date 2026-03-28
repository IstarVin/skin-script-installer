package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
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
 * Restores the original files after a skin script installation.
 * - Files that were newly added (wasOverwrite=false) → deleted from ML path
 * - Files that were overwritten (wasOverwrite=true) → restored from backup
 */
class RestoreScriptUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository,
    private val shizukuManager: ShizukuManager
) {
    private val _progress = MutableStateFlow<InstallProgress?>(null)
    val progress: StateFlow<InstallProgress?> = _progress.asStateFlow()

    suspend fun execute(installationId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = shizukuManager.fileService.value
                ?: return@withContext Result.failure(Exception("Shizuku file service not available"))

            val result = restoreInstallationRecursive(
                installationId = installationId,
                service = service,
                failIfAlreadyRestored = true,
                inProgress = mutableSetOf(),
                restored = mutableSetOf()
            )
            if (result.isFailure) {
                _progress.value = null
            }
            result
        } catch (e: Exception) {
            _progress.value = null
            Result.failure(e)
        }
    }

    private suspend fun restoreInstallationRecursive(
        installationId: Long,
        service: IFileService,
        failIfAlreadyRestored: Boolean,
        inProgress: MutableSet<Long>,
        restored: MutableSet<Long>
    ): Result<Unit> {
        if (installationId in restored) {
            return Result.success(Unit)
        }

        if (!inProgress.add(installationId)) {
            return Result.failure(Exception("Restore aborted: cyclic supersede chain detected"))
        }

        try {
            val installation = repository.getInstallationById(installationId)
                ?: return Result.failure(Exception("Installation not found"))

            if (installation.status == InstallationStatus.RESTORED) {
                return if (failIfAlreadyRestored) {
                    Result.failure(Exception("Installation already restored"))
                } else {
                    restored += installationId
                    Result.success(Unit)
                }
            }

            // If this installation has been superseded, unwind newer owners first.
            val supersedingInstallationIds = repository.getSupersedingInstallationIds(installationId)
            for (supersedingInstallationId in supersedingInstallationIds) {
                val dependencyResult = restoreInstallationRecursive(
                    installationId = supersedingInstallationId,
                    service = service,
                    failIfAlreadyRestored = false,
                    inProgress = inProgress,
                    restored = restored
                )
                if (dependencyResult.isFailure) {
                    return dependencyResult
                }
            }

            val installedFiles = repository.getActiveInstalledFilesByInstallation(installationId)
            if (installedFiles.isEmpty()) {
                return Result.failure(Exception("No active installed files to restore"))
            }

            installedFiles.forEach { file ->
                if (!file.destPath.belongsToUser(installation.userId)) {
                    return Result.failure(
                        Exception("Restore aborted: installation contains mismatched user file path")
                    )
                }
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

            repository.updateInstallation(
                installation.copy(
                    status = InstallationStatus.RESTORED,
                    restoredAt = System.currentTimeMillis()
                )
            )

            val reactivatedInstallationIds = repository.reactivateInstalledFilesSupersededBy(installationId)
            reactivatedInstallationIds.forEach { reactivatedInstallationId ->
                val reactivatedInstallation = repository.getInstallationById(reactivatedInstallationId)
                    ?: return@forEach
                val activeFileCount = repository.countActiveInstalledFiles(reactivatedInstallationId)
                if (
                    activeFileCount > 0 &&
                    reactivatedInstallation.status == InstallationStatus.SUPERSEDED
                ) {
                    repository.updateInstallation(
                        reactivatedInstallation.copy(status = InstallationStatus.INSTALLED)
                    )
                }
            }

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

            restored += installationId
            return Result.success(Unit)
        } finally {
            inProgress.remove(installationId)
        }
    }

    fun resetProgress() {
        _progress.value = null
    }

    private fun String.belongsToUser(userId: Int): Boolean {
        val expectedPrefix = "/storage/emulated/$userId/"
        return startsWith(expectedPrefix)
    }
}

