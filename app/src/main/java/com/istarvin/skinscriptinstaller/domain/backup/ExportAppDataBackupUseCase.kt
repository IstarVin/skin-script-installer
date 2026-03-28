package com.istarvin.skinscriptinstaller.domain.backup

import android.content.Context
import android.net.Uri
import com.istarvin.skinscriptinstaller.BuildConfig
import com.istarvin.skinscriptinstaller.data.db.AppDatabase
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class ExportAppDataBackupUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository
) {
    suspend fun execute(outputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val tempRoot = File(context.cacheDir, "backup_export_${UUID.randomUUID()}")
        val tempArchive = File(context.cacheDir, "backup_export_${UUID.randomUUID()}.zip")

        try {
            val scripts = repository.getAllScriptsOnce()
            val installations = repository.getAllInstallationsOnce()
            val installedFiles = repository.getAllInstalledFilesOnce()
            val heroes = repository.getAllHeroesOnce()
            val skins = repository.getAllSkinsOnce()

            val filesDir = context.filesDir
            val archiveFilesRoot = File(tempRoot, "files")
            val archiveScriptsRoot = File(archiveFilesRoot, "scripts")
            val archiveBackupsRoot = File(archiveFilesRoot, "backups")
            archiveScriptsRoot.mkdirs()
            archiveBackupsRoot.mkdirs()

            scripts.forEach { script ->
                val sourceScriptDir = File(script.storagePath)
                val targetDir = File(archiveScriptsRoot, script.id.toString())
                BackupArchiveUtils.copyDirectory(sourceScriptDir, targetDir)
            }

            BackupArchiveUtils.copyDirectory(
                sourceDir = File(filesDir, "backups"),
                destinationDir = archiveBackupsRoot
            )

            val manifest = AppBackupManifest(
                formatVersion = BackupCompatibilityRegistry.CURRENT_BACKUP_FORMAT_VERSION,
                exportedAt = System.currentTimeMillis(),
                appVersionCode = BuildConfig.VERSION_CODE,
                appVersionName = BuildConfig.VERSION_NAME,
                databaseVersion = AppDatabase.DATABASE_VERSION,
                scripts = scripts.map { script ->
                    SkinScriptBackupRecord(
                        id = script.id,
                        name = script.name,
                        importedAt = script.importedAt,
                        relativeStoragePath = "scripts/${script.id}",
                        heroId = script.heroId,
                        originalSkinId = script.originalSkinId,
                        replacementSkinId = script.replacementSkinId
                    )
                },
                installations = installations.map { installation ->
                    InstallationBackupRecord(
                        id = installation.id,
                        scriptId = installation.scriptId,
                        userId = installation.userId,
                        installedAt = installation.installedAt,
                        restoredAt = installation.restoredAt,
                        status = installation.status
                    )
                },
                installedFiles = installedFiles.map { installedFile ->
                    InstalledFileBackupRecord(
                        id = installedFile.id,
                        installationId = installedFile.installationId,
                        destPath = installedFile.destPath,
                        wasOverwrite = installedFile.wasOverwrite,
                        backupRelativePath = installedFile.backupPath
                            ?.let { toRelativePathOrNull(it, filesDir) },
                        supersededByInstallationId = installedFile.supersededByInstallationId
                    )
                },
                heroes = heroes.map { hero ->
                    HeroBackupRecord(
                        id = hero.id,
                        name = hero.name,
                        heroIcon = hero.heroIcon
                    )
                },
                skins = skins.map { skin ->
                    SkinBackupRecord(
                        id = skin.id,
                        heroId = skin.heroId,
                        name = skin.name
                    )
                }
            )

            val manifestFile = File(tempRoot, "manifest.json")
            manifestFile.parentFile?.mkdirs()
            manifestFile.writeText(BackupJsonCodec.encode(manifest))

            tempArchive.parentFile?.mkdirs()
            FileOutputStream(tempArchive).use { output ->
                BackupArchiveUtils.zipDirectory(tempRoot, output)
            }

            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                tempArchive.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open selected backup destination"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempRoot.deleteRecursively()
            tempArchive.delete()
        }
    }

    private fun toRelativePathOrNull(absolutePath: String, filesDir: File): String? {
        return runCatching {
            File(absolutePath).canonicalFile
                .relativeTo(filesDir.canonicalFile)
                .invariantSeparatorsPath
        }.getOrNull()
    }
}
