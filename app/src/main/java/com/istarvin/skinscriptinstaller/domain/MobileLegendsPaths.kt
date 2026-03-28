package com.istarvin.skinscriptinstaller.domain

import java.io.File

const val ML_ASSETS_RELATIVE_PATH = "Android/data/com.mobile.legends/files/dragon2017/assets"

fun resolveImportedAssetsDir(storagePath: String): File {
    return File(storagePath, ML_ASSETS_RELATIVE_PATH)
}

fun buildMlAssetsRoot(userId: Int): String {
    return "/storage/emulated/$userId/$ML_ASSETS_RELATIVE_PATH"
}

fun installedFileRelativePath(userId: Int, destPath: String): String? {
    val prefix = "${buildMlAssetsRoot(userId)}/"
    return if (destPath.startsWith(prefix)) destPath.removePrefix(prefix) else null
}