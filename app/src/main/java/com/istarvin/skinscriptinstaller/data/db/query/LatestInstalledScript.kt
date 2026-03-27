package com.istarvin.skinscriptinstaller.data.db.query

data class LatestInstalledScript(
    val installationId: Long,
    val scriptId: Long,
    val scriptName: String
)
