package com.istarvin.skinscriptinstaller.domain.backup

data class AppBackupManifest(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val databaseVersion: Int,
    val scripts: List<SkinScriptBackupRecord>,
    val installations: List<InstallationBackupRecord>,
    val installedFiles: List<InstalledFileBackupRecord>
)

data class SkinScriptBackupRecord(
    val id: Long,
    val name: String,
    val importedAt: Long,
    val relativeStoragePath: String
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

data class BackupCompatibilityResult(
    val isCompatible: Boolean,
    val reason: String? = null
)
