package com.istarvin.skinscriptinstaller.data.db.query

data class HeroInstallationConflict(
    val installationId: Long,
    val scriptId: Long,
    val scriptName: String
)
