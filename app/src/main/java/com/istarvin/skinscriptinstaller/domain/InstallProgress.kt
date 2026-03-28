package com.istarvin.skinscriptinstaller.domain

/**
 * Represents the progress of an installation or restore operation.
 */
data class InstallProgress(
    val currentIndex: Int,
    val total: Int,
    val currentFileName: String,
    val isComplete: Boolean = false
)

