package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ArchiveService
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Imports a skin script folder from SAF into internal storage.
 *
 * Ports the Python `validate_and_fix_script_structure` logic:
 * - If the selected folder already contains `Android/data/com.mobile.legends/...`, use as-is.
 * - Otherwise, scan for an `Art` folder and wrap it in the expected ML path structure.
 */
class ImportScriptUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ScriptRepository,
    private val archiveService: ArchiveService
) {
    companion object {
        private const val ML_ASSETS_PREFIX =
            "Android/data/com.mobile.legends/files/dragon2017/assets"
        private const val MISSING_ART_ERROR =
            "Invalid script: expected an Art folder in the selected content"
    }

    suspend fun execute(treeUri: Uri): Result<SkinScript> = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Cannot read selected folder"))

            val scriptId = UUID.randomUUID().toString()
            val scriptDir = File(context.filesDir, "scripts/$scriptId")

            // Determine the structure type
            val structureInfo = detectStructure(rootDoc)

            if (structureInfo.type == StructureType.INVALID_NO_ART) {
                return@withContext Result.failure(IllegalArgumentException(MISSING_ART_ERROR))
            }

            when (structureInfo.type) {
                StructureType.FULL_PATH -> {
                    // Already has Android/data/com.mobile.legends/... structure — copy as-is
                    copyDocumentTree(rootDoc, scriptDir)
                }

                StructureType.ART_FOLDER -> {
                    // Art folder found (possibly nested) — copy from its parent into the ML path,
                    // mirroring the Python logic that uses art_parent_path as the source.
                    val destAssetsDir = File(scriptDir, ML_ASSETS_PREFIX)
                    destAssetsDir.mkdirs()
                    val sourceDoc = structureInfo.artParentDoc ?: rootDoc
                    copyDocumentTree(sourceDoc, destAssetsDir)
                }

                StructureType.INVALID_NO_ART -> Unit
            }

            val name = rootDoc.name ?: "Unknown Script"
            val script = SkinScript(
                name = name,
                storagePath = scriptDir.absolutePath
            )
            val insertedId = repository.insertScript(script)
            val insertedScript = script.copy(id = insertedId)

            Result.success(insertedScript)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeZip(zipUri: Uri, password: CharArray? = null): Result<SkinScript> =
        withContext(Dispatchers.IO) {
            val tempZipFile = File(context.cacheDir, "import_${UUID.randomUUID()}.zip")
            val tempExtractDir = File(context.cacheDir, "extract_${UUID.randomUUID()}")

            try {
                copyUriToTempZip(zipUri, tempZipFile)

                val inspection = archiveService.inspectZip(tempZipFile).getOrElse {
                    return@withContext Result.failure(it)
                }

                if (inspection.encrypted && password == null) {
                    return@withContext Result.failure(PasswordRequiredException())
                }

                archiveService.extractZip(tempZipFile, tempExtractDir, password).getOrElse {
                    return@withContext Result.failure(it)
                }

                val extractedRoot = resolveExtractedRoot(tempExtractDir)
                importFromFileRoot(extractedRoot, zipUri)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                tempZipFile.delete()
                if (tempExtractDir.exists()) {
                    tempExtractDir.deleteRecursively()
                }
                password?.fill('\u0000')
            }
        }

    private fun copyUriToTempZip(uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open selected ZIP file")
    }

    private suspend fun importFromFileRoot(rootDir: File, sourceUri: Uri): Result<SkinScript> {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return Result.failure(Exception("ZIP archive is empty"))
        }

        val scriptId = UUID.randomUUID().toString()
        val scriptDir = File(context.filesDir, "scripts/$scriptId")
        val structureInfo = detectFileStructure(rootDir)

        if (structureInfo.type == StructureType.INVALID_NO_ART) {
            return Result.failure(IllegalArgumentException(MISSING_ART_ERROR))
        }

        when (structureInfo.type) {
            StructureType.FULL_PATH -> {
                copyFileTree(rootDir, scriptDir)
            }

            StructureType.ART_FOLDER -> {
                val destAssetsDir = File(scriptDir, ML_ASSETS_PREFIX)
                destAssetsDir.mkdirs()
                val sourceDir = structureInfo.artParentDir ?: rootDir
                copyFileTree(sourceDir, destAssetsDir)
            }

            StructureType.INVALID_NO_ART -> Unit
        }

        val scriptName = queryDisplayName(sourceUri)?.removeSuffix(".zip") ?: rootDir.name
        val script = SkinScript(
            name = scriptName ?: "Imported ZIP",
            storagePath = scriptDir.absolutePath
        )
        val insertedId = repository.insertScript(script)
        return Result.success(script.copy(id = insertedId))
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun resolveExtractedRoot(extractDir: File): File {
        val children = extractDir.listFiles()?.filter { it.exists() } ?: return extractDir
        return if (children.size == 1 && children.first().isDirectory) {
            children.first()
        } else {
            extractDir
        }
    }

    private fun detectFileStructure(rootDir: File): StructureInfo {
        val fullPathDir = File(rootDir, "Android/data/com.mobile.legends")
        val artParent = findArtFolderParent(rootDir)

        if (fullPathDir.exists() && fullPathDir.isDirectory && artParent != null) {
            return StructureInfo(StructureType.FULL_PATH)
        }

        if (artParent != null) {
            return StructureInfo(StructureType.ART_FOLDER, artParentDir = artParent)
        }

        return StructureInfo(StructureType.INVALID_NO_ART)
    }

    private fun detectStructure(rootDoc: DocumentFile): StructureInfo {
        // Check if root contains "Android" directory leading to ML path
        val androidDir = rootDoc.findFile("Android")
        val artParent = findArtFolderParent(rootDoc)

        if (androidDir != null && androidDir.isDirectory) {
            val dataDir = androidDir.findFile("data")
            if (dataDir != null && dataDir.isDirectory) {
                val mlDir = dataDir.findFile("com.mobile.legends")
                if (mlDir != null && mlDir.isDirectory && artParent != null) {
                    return StructureInfo(StructureType.FULL_PATH)
                }
            }
        }

        // Recursively walk the tree to find an Art folder (mirrors os.walk in Python).
        // Returns the *parent* of the Art folder so we copy from there.
        if (artParent != null) {
            return StructureInfo(StructureType.ART_FOLDER, artParent)
        }

        return StructureInfo(StructureType.INVALID_NO_ART)
    }

    /**
     * Recursively searches for a directory named "Art" and returns its parent [DocumentFile].
     * Mirrors the `os.walk` loop in the Python reference: the first match wins.
     */
    private fun findArtFolderParent(doc: DocumentFile): DocumentFile? {
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                if (child.name.equals("Art", ignoreCase = true)) {
                    return doc  // doc is the parent that contains "Art"
                }
                // Recurse into subdirectory
                val found = findArtFolderParent(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findArtFolderParent(dir: File): File? {
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isDirectory) {
                if (child.name.equals("Art", ignoreCase = true)) {
                    return dir
                }
                val found = findArtFolderParent(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun copyDocumentTree(sourceDoc: DocumentFile, destDir: File) {
        destDir.mkdirs()
        for (child in sourceDoc.listFiles()) {
            val childName = child.name ?: continue
            if (child.isDirectory) {
                copyDocumentTree(child, File(destDir, childName))
            } else {
                val destFile = File(destDir, childName)
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun copyFileTree(sourceDir: File, destDir: File) {
        destDir.mkdirs()
        val children = sourceDir.listFiles() ?: return
        for (child in children) {
            val destination = File(destDir, child.name)
            if (child.isDirectory) {
                copyFileTree(child, destination)
            } else if (child.isFile) {
                destination.parentFile?.mkdirs()
                child.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private data class StructureInfo(
        val type: StructureType,
        val artParentDoc: DocumentFile? = null,
        val artParentDir: File? = null
    )

    private enum class StructureType {
        FULL_PATH,   // Contains Android/data/com.mobile.legends/...
        ART_FOLDER,  // Art/ folder found (possibly nested) — artParentDoc holds its parent
        INVALID_NO_ART
    }
}

