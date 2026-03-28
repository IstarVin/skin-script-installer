package com.istarvin.skinscriptinstaller.domain

import java.io.File

data class PlannedScriptFile(
    val sourceFile: File,
    val relativePath: String,
    val destPath: String
)

data class ScriptInstallPlan(
    val assetsDir: File,
    val files: List<PlannedScriptFile>
)

enum class FileConflictChoice {
    KEEP_CURRENT,
    USE_NEW
}

fun buildScriptInstallPlan(storagePath: String, userId: Int): Result<ScriptInstallPlan> {
    val assetsDir = resolveImportedAssetsDir(storagePath)
    if (!assetsDir.exists() || !assetsDir.isDirectory) {
        return Result.failure(
            Exception("Script has no assets directory at: ${assetsDir.path}")
        )
    }

    val plannedFiles = mutableListOf<PlannedScriptFile>()
    collectPlannedScriptFiles(
        rootDir = assetsDir,
        currentDir = assetsDir,
        targetAssetsRoot = buildMlAssetsRoot(userId),
        result = plannedFiles
    )

    if (plannedFiles.isEmpty()) {
        return Result.failure(Exception("No files to install"))
    }

    return Result.success(
        ScriptInstallPlan(
            assetsDir = assetsDir,
            files = plannedFiles
        )
    )
}

private fun collectPlannedScriptFiles(
    rootDir: File,
    currentDir: File,
    targetAssetsRoot: String,
    result: MutableList<PlannedScriptFile>
) {
    currentDir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            collectPlannedScriptFiles(rootDir, file, targetAssetsRoot, result)
        } else {
            val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
            result += PlannedScriptFile(
                sourceFile = file,
                relativePath = relativePath,
                destPath = "$targetAssetsRoot/$relativePath"
            )
        }
    }
}