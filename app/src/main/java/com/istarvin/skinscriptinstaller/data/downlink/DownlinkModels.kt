package com.istarvin.skinscriptinstaller.data.downlink

data class DownlinkRepositoryEntry(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val heroName: String,
    val heroIcon: String?,
    val replacementSkinName: String,
    val originalSkinName: String,
    val sfileUrl: String
)
