package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    private val repository: ScriptRepository
) {
    companion object {
        private const val ML_ASSETS_PREFIX =
            "Android/data/com.mobile.legends/files/dragon2017/assets"
    }

    suspend fun execute(treeUri: Uri): Result<SkinScript> = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Cannot read selected folder"))

            val scriptId = UUID.randomUUID().toString()
            val scriptDir = File(context.filesDir, "scripts/$scriptId")

            // Determine the structure type
            val structureInfo = detectStructure(rootDoc)

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

                StructureType.UNKNOWN -> {
                    // Assume it's raw asset content — wrap it in the ML path
                    val destAssetsDir = File(scriptDir, ML_ASSETS_PREFIX)
                    destAssetsDir.mkdirs()
                    copyDocumentTree(rootDoc, destAssetsDir)
                }
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

    private fun detectStructure(rootDoc: DocumentFile): StructureInfo {
        // Check if root contains "Android" directory leading to ML path
        val androidDir = rootDoc.findFile("Android")
        if (androidDir != null && androidDir.isDirectory) {
            val dataDir = androidDir.findFile("data")
            if (dataDir != null && dataDir.isDirectory) {
                val mlDir = dataDir.findFile("com.mobile.legends")
                if (mlDir != null && mlDir.isDirectory) {
                    return StructureInfo(StructureType.FULL_PATH)
                }
            }
        }

        // Recursively walk the tree to find an Art folder (mirrors os.walk in Python).
        // Returns the *parent* of the Art folder so we copy from there.
        val artParent = findArtFolderParent(rootDoc)
        if (artParent != null) {
            return StructureInfo(StructureType.ART_FOLDER, artParent)
        }

        return StructureInfo(StructureType.UNKNOWN)
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

    private data class StructureInfo(
        val type: StructureType,
        val artParentDoc: DocumentFile? = null
    )

    private enum class StructureType {
        FULL_PATH,   // Contains Android/data/com.mobile.legends/...
        ART_FOLDER,  // Art/ folder found (possibly nested) — artParentDoc holds its parent
        UNKNOWN      // Raw content — treat same as ART_FOLDER
    }
}

