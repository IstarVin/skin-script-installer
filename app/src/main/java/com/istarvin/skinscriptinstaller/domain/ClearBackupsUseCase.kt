package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Clears backup files and resets installation state so scripts appear
 * as "not installed" in the app, without removing them from the script list.
 *
 * - Deletes physical backup files from filesDir/backups/
 * - Marks all non-restored installations as RESTORED (no-op state)
 * - Nulls out backupPath on installed file records (via DB update)
 */
class ClearBackupsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository
) {
    /**
     * Clears all backups for every script — global reset.
     */
    suspend fun clearAll(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rootBackupDir = File(context.filesDir, "backups")
            rootBackupDir.deleteRecursively()

            val installations = repository.getAllInstallationsOnce()
            var count = 0
            installations.forEach { installation ->
                if (installation.status != InstallationStatus.RESTORED &&
                    installation.status != InstallationStatus.NOT_INSTALLED
                ) {
                    repository.updateInstallation(
                        installation.copy(
                            status = InstallationStatus.NOT_INSTALLED,
                            restoredAt = System.currentTimeMillis()
                        )
                    )
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears backups for a single script by scriptId.
     */
    suspend fun clearForScript(scriptId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val installations = repository.getAllInstallationsOnce()
                .filter { it.scriptId == scriptId }

            installations.forEach { installation ->
                val backupDir = File(context.filesDir, "backups/${installation.id}")
                backupDir.deleteRecursively()

                if (installation.status != InstallationStatus.RESTORED &&
                    installation.status != InstallationStatus.NOT_INSTALLED
                ) {
                    repository.updateInstallation(
                        installation.copy(
                            status = InstallationStatus.NOT_INSTALLED,
                            restoredAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
