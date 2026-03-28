package com.istarvin.skinscriptinstaller.data.db.query

data class FileOwnershipConflict(
    val installedFileId: Long,
    val installationId: Long,
    val scriptId: Long,
    val scriptName: String,
    val destPath: String,
    val installedAt: Long
)