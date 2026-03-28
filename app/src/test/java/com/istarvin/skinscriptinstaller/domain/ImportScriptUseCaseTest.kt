package com.istarvin.skinscriptinstaller.domain

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ArchiveInspection
import com.istarvin.skinscriptinstaller.service.ArchiveService
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportScriptUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: ScriptRepository
    private lateinit var archiveService: ArchiveService
    private lateinit var useCase: ImportScriptUseCase

    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    companion object {
        private const val ML_ASSETS_PREFIX =
            "Android/data/com.mobile.legends/files/dragon2017/assets"
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        archiveService = mockk(relaxed = true)

        filesDir = tempFolder.newFolder("filesDir")
        cacheDir = tempFolder.newFolder("cacheDir")
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver

        coEvery { repository.insertScript(any()) } returns 1L

        useCase = ImportScriptUseCase(context, repository, archiveService)
    }

    // --- executeZip - structure detection ---

    @Test
    fun `executeZip detects FULL_PATH structure`() = runTest {
        val extractDir = createExtractedDir(
            "$ML_ASSETS_PREFIX/Art/texture.png" to "texdata"
        )
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "MyScript.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isSuccess)
        val script = result.getOrThrow()
        assertEquals("MyScript", script.name)

        // Verify the script dir contains the full ML path structure
        val scriptDir = File(script.storagePath)
        val assetsDir = File(scriptDir, ML_ASSETS_PREFIX)
        assertTrue("Assets dir should exist at ${assetsDir.absolutePath}", assetsDir.exists())
        assertTrue(File(assetsDir, "Art/texture.png").exists())
    }

    @Test
    fun `executeZip detects ART_FOLDER structure and wraps in ML path`() = runTest {
        val extractDir = createExtractedDir(
            "Art/texture.png" to "texdata",
            "Art/sub/model.obj" to "modeldata",
            "readme.txt" to "info"  // sibling prevents resolveExtractedRoot from unwrapping Art
        )
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "ArtOnly.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isSuccess)
        val script = result.getOrThrow()

        // Should wrap Art folder inside ML assets path
        val scriptDir = File(script.storagePath)
        val artDir = File(scriptDir, "$ML_ASSETS_PREFIX/Art")
        assertTrue("Art should be inside ML assets path: ${artDir.absolutePath}", artDir.exists())
        assertTrue(File(artDir, "texture.png").exists())
        assertTrue(File(artDir, "sub/model.obj").exists())
    }

    @Test
    fun `executeZip returns failure for INVALID_NO_ART structure`() = runTest {
        val extractDir = createExtractedDir(
            "random/file.txt" to "data",
            "other/stuff.bin" to "binary"
        )
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "NoArt.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Art folder") == true)
    }

    @Test
    fun `executeZip finds nested Art folder`() = runTest {
        val extractDir = createExtractedDir(
            "deep/nested/Art/texture.png" to "texdata"
        )
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "Nested.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isSuccess)
        val scriptDir = File(result.getOrThrow().storagePath)
        // The "artParent" is "deep/nested", so copying from there into ML_ASSETS_PREFIX
        val artDir = File(scriptDir, "$ML_ASSETS_PREFIX/Art")
        assertTrue("Nested Art folder should be found", artDir.exists())
    }

    // --- executeZip - password handling ---

    @Test
    fun `executeZip returns PasswordRequiredException when encrypted and no password`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(10))
        every { archiveService.inspectZip(any()) } returns Result.success(ArchiveInspection(encrypted = true))

        val result = useCase.executeZip(uri, password = null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PasswordRequiredException)
    }

    @Test
    fun `executeZip returns failure when extraction fails`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(10))
        every { archiveService.inspectZip(any()) } returns Result.success(ArchiveInspection(encrypted = false))
        every { archiveService.extractZip(any(), any(), any()) } returns Result.failure(Exception("Corrupt"))

        val result = useCase.executeZip(uri)

        assertTrue(result.isFailure)
        assertEquals("Corrupt", result.exceptionOrNull()?.message)
    }

    // --- executeZip - single child root unwrapping ---

    @Test
    fun `executeZip unwraps single-child root directory`() = runTest {
        // Create extract dir with a single child dir that contains the actual content
        val extractDir = tempFolder.newFolder("extract_${System.nanoTime()}")
        val wrapperDir = File(extractDir, "ScriptName")
        val artDir = File(wrapperDir, "Art")
        artDir.mkdirs()
        File(artDir, "texture.png").writeText("texdata")

        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "Wrapped.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isSuccess)
        // The single child "ScriptName" should have been unwrapped as the root
        val scriptDir = File(result.getOrThrow().storagePath)
        assertTrue(File(scriptDir, "$ML_ASSETS_PREFIX/Art/texture.png").exists())
    }

    // --- executeZip - cleanup ---

    @Test
    fun `executeZip cleans up temp files on success`() = runTest {
        val extractDir = createExtractedDir("Art/file.png" to "data")
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "Clean.zip")

        useCase.executeZip(uri)

        // Verify no temp zip or extract dir remains in cache
        val cacheFiles = cacheDir.listFiles()?.filter {
            it.name.startsWith("import_") || it.name.startsWith("extract_")
        } ?: emptyList()
        assertTrue("Cache should be cleaned up", cacheFiles.isEmpty())
    }

    @Test
    fun `executeZip clears password array after use`() = runTest {
        val extractDir = createExtractedDir("Art/file.png" to "data")
        setupZipMocks(extractDir, encrypted = false)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "PassClear.zip")

        val password = "secret".toCharArray()
        useCase.executeZip(uri, password)

        assertTrue("Password should be cleared", password.all { it == '\u0000' })
    }

    @Test
    fun `executeZip uses display name from URI as script name`() = runTest {
        val extractDir = createExtractedDir(
            "Art/file.png" to "data",
            "readme.txt" to "info"  // sibling prevents unwrapping
        )
        setupZipMocks(extractDir)

        val uri = mockk<Uri>(relaxed = true)
        setupDisplayName(uri, "My Cool Script.zip")

        val result = useCase.executeZip(uri)

        assertTrue(result.isSuccess)
        assertEquals("My Cool Script", result.getOrThrow().name)
    }

    @Test
    fun `executeZip returns failure for empty zip`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(10))
        every { archiveService.inspectZip(any()) } returns Result.success(ArchiveInspection(encrypted = false))

        // Extract to a non-existent directory (simulates empty extraction)
        every { archiveService.extractZip(any(), any(), any()) } answers {
            val destDir = secondArg<File>()
            destDir.mkdirs()
            // Empty directory — no files extracted
            Result.success(Unit)
        }

        val result = useCase.executeZip(uri)

        assertTrue(result.isFailure)
    }

    @Test
    fun `detectFileConflicts returns matching relative paths across existing scripts`() = runTest {
        val preparedDir = createScriptStorage(
            "prepared",
            "Art/shared.png" to "new",
            "Art/new_only.png" to "unique"
        )
        val existing1Dir = createScriptStorage(
            "existing1",
            "Art/shared.png" to "old-1"
        )
        val existing2Dir = createScriptStorage(
            "existing2",
            "Art/other.png" to "old-2"
        )

        coEvery { repository.getAllScriptsOnce() } returns listOf(
            SkinScript(id = 10L, name = "Old Script A", storagePath = existing1Dir.absolutePath),
            SkinScript(id = 11L, name = "Old Script B", storagePath = existing2Dir.absolutePath)
        )

        val result = useCase.detectFileConflicts(
            preparedImport = ImportedScriptPayload(
                name = "Prepared",
                storagePath = preparedDir.absolutePath
            )
        )

        assertTrue(result.isSuccess)
        val conflicts = result.getOrThrow()
        assertEquals(1, conflicts.size)
        assertEquals("Art/shared.png", conflicts.first().relativePath)
        assertEquals(listOf("Old Script A"), conflicts.first().existingScripts.map { it.scriptName })
    }

    @Test
    fun `applyConflictChoices keep existing removes file from prepared import`() = runTest {
        val preparedDir = createScriptStorage(
            "prepared_keep",
            "Art/shared.png" to "new",
            "Art/new_only.png" to "unique"
        )

        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 10L, scriptName = "Existing Script")
                )
            )
        )

        val result = useCase.applyConflictChoices(
            preparedImport = ImportedScriptPayload("Prepared", preparedDir.absolutePath),
            conflicts = conflicts,
            choices = mapOf("Art/shared.png" to ImportConflictResolutionChoice.KEEP_EXISTING)
        )

        assertTrue(result.isSuccess)
        val preparedAssetsDir = File(preparedDir, ML_ASSETS_PREFIX)
        assertFalse(File(preparedAssetsDir, "Art/shared.png").exists())
        assertTrue(File(preparedAssetsDir, "Art/new_only.png").exists())
    }

    @Test
    fun `applyConflictChoices use imported deletes existing conflicting files`() = runTest {
        val preparedDir = createScriptStorage(
            "prepared_use",
            "Art/shared.png" to "new"
        )
        val existing1Dir = createScriptStorage(
            "existing_use_1",
            "Art/shared.png" to "old-1"
        )
        val existing2Dir = createScriptStorage(
            "existing_use_2",
            "Art/shared.png" to "old-2"
        )

        coEvery { repository.getScriptById(10L) } returns
            SkinScript(id = 10L, name = "Existing A", storagePath = existing1Dir.absolutePath)
        coEvery { repository.getScriptById(11L) } returns
            SkinScript(id = 11L, name = "Existing B", storagePath = existing2Dir.absolutePath)

        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 10L, scriptName = "Existing A"),
                    ImportConflictScriptRef(scriptId = 11L, scriptName = "Existing B")
                )
            )
        )

        val result = useCase.applyConflictChoices(
            preparedImport = ImportedScriptPayload("Prepared", preparedDir.absolutePath),
            conflicts = conflicts,
            choices = mapOf("Art/shared.png" to ImportConflictResolutionChoice.USE_IMPORTED)
        )

        assertTrue(result.isSuccess)
        assertFalse(File(existing1Dir, "$ML_ASSETS_PREFIX/Art/shared.png").exists())
        assertFalse(File(existing2Dir, "$ML_ASSETS_PREFIX/Art/shared.png").exists())
        assertTrue(File(preparedDir, "$ML_ASSETS_PREFIX/Art/shared.png").exists())
    }

    @Test
    fun `applyConflictChoices fails when all prepared files are skipped`() = runTest {
        val preparedDir = createScriptStorage(
            "prepared_empty",
            "Art/shared.png" to "new"
        )

        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 10L, scriptName = "Existing Script")
                )
            )
        )

        val result = useCase.applyConflictChoices(
            preparedImport = ImportedScriptPayload("Prepared", preparedDir.absolutePath),
            conflicts = conflicts,
            choices = mapOf("Art/shared.png" to ImportConflictResolutionChoice.KEEP_EXISTING)
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Nothing left to import") == true
        )
    }

    // --- Helpers ---

    private fun createScriptStorage(name: String, vararg files: Pair<String, String>): File {
        val rootDir = tempFolder.newFolder("${name}_${System.nanoTime()}")
        val assetsDir = File(rootDir, ML_ASSETS_PREFIX)
        assetsDir.mkdirs()
        files.forEach { (relativePath, content) ->
            val target = File(assetsDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return rootDir
    }

    private fun createExtractedDir(vararg files: Pair<String, String>): File {
        val extractDir = tempFolder.newFolder("extract_${System.nanoTime()}")
        files.forEach { (path, content) ->
            val file = File(extractDir, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        return extractDir
    }

    private fun setupZipMocks(extractDir: File, encrypted: Boolean = false) {
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(ByteArray(10))
        every { archiveService.inspectZip(any()) } returns Result.success(
            ArchiveInspection(encrypted = encrypted)
        )
        every { archiveService.extractZip(any(), any(), any()) } answers {
            val destDir = secondArg<File>()
            // Copy extractDir contents to destDir
            extractDir.copyRecursively(destDir, overwrite = true)
            Result.success(Unit)
        }
    }

    private fun setupDisplayName(uri: Uri, displayName: String) {
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        cursor.addRow(arrayOf(displayName))
        every {
            contentResolver.query(uri, any(), any(), any(), any())
        } returns cursor
    }
}
