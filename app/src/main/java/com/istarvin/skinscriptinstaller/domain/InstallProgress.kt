package com.istarvin.skinscriptinstaller.domain

/**
 * Represents the progress of an install or restore operation.
 */
data class InstallProgress(
    val currentIndex: Int,
    val total: Int,
    val currentFileName: String,
    val isComplete: Boolean = false
)

