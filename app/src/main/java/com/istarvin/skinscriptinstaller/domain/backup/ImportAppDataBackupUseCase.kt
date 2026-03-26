package com.istarvin.skinscriptinstaller.domain.backup

import android.content.Context
import android.net.Uri
import com.istarvin.skinscriptinstaller.BuildConfig
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.db.migrations.DatabaseMigrations
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class ImportAppDataBackupUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository
) {
    suspend fun execute(inputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val tempArchive = File(context.cacheDir, "backup_import_${UUID.randomUUID()}.zip")
        val extractRoot = File(context.cacheDir, "backup_import_${UUID.randomUUID()}")

        try {
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                tempArchive.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open selected backup file"))

            BackupArchiveUtils.unzip(tempArchive, extractRoot)

            val manifestFile = File(extractRoot, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("Invalid backup: missing manifest.json"))
            }

            val manifest = BackupJsonCodec.decode(manifestFile.readText())
            val compatibility = BackupCompatibilityRegistry.validate(
                manifest = manifest,
                migrationEdges = DatabaseMigrations.availablePaths
            )

            if (!compatibility.isCompatible) {
                return@withContext Result.failure(
                    Exception(compatibility.reason ?: "Backup is not compatible")
                )
            }

            if (manifest.appVersionCode > BuildConfig.VERSION_CODE) {
                return@withContext Result.failure(
                    Exception("Backup was created by a newer app version and cannot be restored safely")
                )
            }

            replaceFilePayload(extractRoot)

            val heroes = manifest.heroes.map { hero ->
                Hero(
                    id = hero.id,
                    name = hero.name
                )
            }

            val skins = manifest.skins.map { skin ->
                Skin(
                    id = skin.id,
                    heroId = skin.heroId,
                    name = skin.name
                )
            }

            val scripts = manifest.scripts.map { script ->
                SkinScript(
                    id = script.id,
                    name = script.name,
                    importedAt = script.importedAt,
                    storagePath = File(context.filesDir, script.relativeStoragePath).absolutePath,
                    heroId = script.heroId,
                    originalSkinId = script.originalSkinId,
                    replacementSkinId = script.replacementSkinId
                )
            }

            val installations = manifest.installations.map { installation ->
                Installation(
                    id = installation.id,
                    scriptId = installation.scriptId,
                    userId = installation.userId,
                    installedAt = installation.installedAt,
                    restoredAt = installation.restoredAt,
                    status = installation.status
                )
            }

            val installedFiles = manifest.installedFiles.map { installedFile ->
                InstalledFile(
                    id = installedFile.id,
                    installationId = installedFile.installationId,
                    destPath = installedFile.destPath,
                    wasOverwrite = installedFile.wasOverwrite,
                    backupPath = installedFile.backupRelativePath
                        ?.let { File(context.filesDir, it).absolutePath }
                )
            }

            repository.replaceAllBackupData(scripts, installations, installedFiles, heroes, skins)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempArchive.delete()
            extractRoot.deleteRecursively()
        }
    }

    private fun replaceFilePayload(extractRoot: File) {
        val extractedScripts = File(extractRoot, "files/scripts")
        val extractedBackups = File(extractRoot, "files/backups")

        val targetScripts = File(context.filesDir, "scripts")
        val targetBackups = File(context.filesDir, "backups")

        targetScripts.deleteRecursively()
        targetBackups.deleteRecursively()

        BackupArchiveUtils.copyDirectory(extractedScripts, targetScripts)
        BackupArchiveUtils.copyDirectory(extractedBackups, targetBackups)
    }
}
