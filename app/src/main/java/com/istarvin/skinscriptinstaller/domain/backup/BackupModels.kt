package com.istarvin.skinscriptinstaller.domain.backup

data class AppBackupManifest(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val databaseVersion: Int,
    val scripts: List<SkinScriptBackupRecord>,
    val installations: List<InstallationBackupRecord>,
    val installedFiles: List<InstalledFileBackupRecord>,
    val heroes: List<HeroBackupRecord> = emptyList(),
    val skins: List<SkinBackupRecord> = emptyList()
)

data class SkinScriptBackupRecord(
    val id: Long,
    val name: String,
    val importedAt: Long,
    val relativeStoragePath: String,
    val heroId: Long? = null,
    val originalSkinId: Long? = null,
    val replacementSkinId: Long? = null
)

data class InstallationBackupRecord(
    val id: Long,
    val scriptId: Long,
    val userId: Int,
    val installedAt: Long,
    val restoredAt: Long?,
    val status: String
)

data class InstalledFileBackupRecord(
    val id: Long,
    val installationId: Long,
    val destPath: String,
    val wasOverwrite: Boolean,
    val backupRelativePath: String?
)

data class HeroBackupRecord(
    val id: Long,
    val name: String,
    val heroIcon: String? = null
)

data class SkinBackupRecord(
    val id: Long,
    val heroId: Long,
    val name: String
)

data class BackupCompatibilityResult(
    val isCompatible: Boolean,
    val reason: String? = null
)
