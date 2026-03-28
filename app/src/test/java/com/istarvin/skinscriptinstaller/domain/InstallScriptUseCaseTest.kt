package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.query.FileOwnershipConflict
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InstallScriptUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: ScriptRepository
    private lateinit var shizukuManager: ShizukuManager
    private lateinit var fileService: IFileService
    private lateinit var fileServiceFlow: MutableStateFlow<IFileService?>
    private lateinit var useCase: InstallScriptUseCase

    private lateinit var filesDir: File

    companion object {
        private const val ML_ASSETS_REL = "Android/data/com.mobile.legends/files/dragon2017/assets"
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        shizukuManager = mockk(relaxed = true)
        fileService = mockk(relaxed = true)

        filesDir = tempFolder.newFolder("filesDir")
        every { context.filesDir } returns filesDir

        fileServiceFlow = MutableStateFlow(fileService)
        every { shizukuManager.fileService } returns fileServiceFlow

        // Mock ParcelFileDescriptor static methods (Android API not available in JVM tests)
        mockkStatic(ParcelFileDescriptor::class)
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { ParcelFileDescriptor.open(any(), any()) } returns mockPfd

        useCase = InstallScriptUseCase(context, repository, shizukuManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(ParcelFileDescriptor::class)
    }

    @Test
    fun `execute returns failure when script not found`() = runTest {
        coEvery { repository.getScriptById(1L) } returns null

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("Script not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when Shizuku service unavailable`() = runTest {
        val script = createScript()
        coEvery { repository.getScriptById(1L) } returns script
        fileServiceFlow.value = null

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("Shizuku file service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when assets directory missing`() = runTest {
        val scriptDir = tempFolder.newFolder("script1")
        val script = createScript(storagePath = scriptDir.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no assets directory") == true)
    }

    @Test
    fun `execute returns failure when no files to install`() = runTest {
        val script = createScriptWithEmptyAssets()
        coEvery { repository.getScriptById(1L) } returns script

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("No files to install", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute succeeds and creates installation record`() = runTest {
        val script = createScriptWithAssets("Art/texture.png" to "texdata")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        val result = useCase.execute(1L, userId = 0)

        assertTrue(result.isSuccess)
        val installation = result.getOrThrow()
        assertEquals(100L, installation.id)
        assertEquals("installed", installation.status)
        assertEquals(1L, installation.scriptId)

        coVerify { repository.insertInstallation(match { it.scriptId == 1L && it.userId == 0 }) }
        coVerify { repository.insertInstalledFiles(match { it.size == 1 }) }
    }

    @Test
    fun `execute installs multiple files`() = runTest {
        val script = createScriptWithAssets(
            "Art/texture1.png" to "tex1",
            "Art/texture2.png" to "tex2",
            "Art/sub/texture3.png" to "tex3"
        )
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        coVerify { repository.insertInstalledFiles(match { it.size == 3 }) }
    }

    @Test
    fun `execute backs up existing files before overwriting`() = runTest {
        val script = createScriptWithAssets("Art/existing.png" to "new data")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns true
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        // Create a real file to serve as backup source — provides a valid FileDescriptor
        // for ParcelFileDescriptor.AutoCloseInputStream(pfd)
        val backupSource = File(tempFolder.root, "backup_content.dat")
        backupSource.writeText("original content")
        val backupFis = FileInputStream(backupSource)
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { mockPfd.fileDescriptor } returns backupFis.fd
        every { fileService.openFileForRead(any()) } returns mockPfd

        val result = useCase.execute(1L)

        backupFis.close()

        assertTrue("Expected success but got: ${result.exceptionOrNull()}", result.isSuccess)
        verify { fileService.openFileForRead(any()) }
        coVerify {
            repository.insertInstalledFiles(match { files ->
                files.all { it.wasOverwrite && it.backupPath != null }
            })
        }
    }

    @Test
    fun `execute skips backup for new files`() = runTest {
        val script = createScriptWithAssets("Art/new.png" to "new data")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { fileService.openFileForRead(any()) }
        coVerify {
            repository.insertInstalledFiles(match { files ->
                files.all { !it.wasOverwrite && it.backupPath == null }
            })
        }
    }

    @Test
    fun `execute writes to correct target path for user 0`() = runTest {
        val script = createScriptWithAssets("Art/texture.png" to "data")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        useCase.execute(1L, userId = 0)

        coVerify {
            repository.insertInstalledFiles(match { files ->
                files.first().destPath.startsWith("/storage/emulated/0/$ML_ASSETS_REL")
            })
        }
    }

    @Test
    fun `execute writes to correct target path for user 10`() = runTest {
        val script = createScriptWithAssets("Art/texture.png" to "data")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        useCase.execute(1L, userId = 10)

        coVerify {
            repository.insertInstalledFiles(match { files ->
                files.first().destPath.startsWith("/storage/emulated/10/$ML_ASSETS_REL")
            })
        }
    }

    @Test
    fun `execute emits progress updates`() = runTest {
        val script = createScriptWithAssets(
            "Art/file1.png" to "data1",
            "Art/file2.png" to "data2"
        )
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true
        coEvery { repository.getActiveFileOwnershipConflicts(any(), any(), any(), any()) } returns emptyList()

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        // After completion, progress should indicate completion
        val progress = useCase.progress.value
        assertNotNull(progress)
        assertTrue(progress!!.isComplete)
        assertEquals(2, progress.total)
    }

    @Test
    fun `execute resets progress on failure`() = runTest {
        coEvery { repository.getScriptById(1L) } throws RuntimeException("DB error")

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertNull(useCase.progress.value)
    }

    @Test
    fun `resetProgress clears progress state`() {
        useCase.resetProgress()
        assertNull(useCase.progress.value)
    }

    @Test
    fun `execute skips conflicting files when keep current is selected`() = runTest {
        val script = createScriptWithAssets(
            "Art/conflict.png" to "new data",
            "Art/fresh.png" to "fresh data"
        )
        val conflictPath = "${buildMlAssetsRoot(0)}/Art/conflict.png"
        val freshPath = "${buildMlAssetsRoot(0)}/Art/fresh.png"
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        coEvery {
            repository.getActiveFileOwnershipConflicts(any(), any(), any(), any())
        } returns listOf(
            FileOwnershipConflict(
                installedFileId = 7L,
                installationId = 5L,
                scriptId = 2L,
                scriptName = "Old Script",
                destPath = conflictPath,
                installedAt = 1L
            )
        )
        every { fileService.exists(any()) } returns false
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true

        val result = useCase.execute(
            scriptId = 1L,
            userId = 0,
            fileConflictChoices = mapOf(conflictPath to FileConflictChoice.KEEP_CURRENT)
        )

        assertTrue(result.isSuccess)
        coVerify {
            repository.insertInstalledFiles(match { files ->
                files.size == 1 && files.single().destPath == freshPath
            })
        }
        coVerify(exactly = 0) { repository.markInstalledFilesSuperseded(any(), any()) }
        verify(exactly = 1) { fileService.writeFile(any(), freshPath) }
    }

    @Test
    fun `execute supersedes previous owner when new file wins conflict`() = runTest {
        val script = createScriptWithAssets("Art/conflict.png" to "new data")
        val conflictPath = "${buildMlAssetsRoot(0)}/Art/conflict.png"
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.insertInstallation(any()) } returns 100L
        coEvery {
            repository.getActiveFileOwnershipConflicts(any(), any(), any(), any())
        } returns listOf(
            FileOwnershipConflict(
                installedFileId = 7L,
                installationId = 5L,
                scriptId = 2L,
                scriptName = "Old Script",
                destPath = conflictPath,
                installedAt = 1L
            )
        )
        coEvery { repository.getInstallationById(5L) } returns com.istarvin.skinscriptinstaller.data.db.entity.Installation(
            id = 5L,
            scriptId = 2L,
            userId = 0,
            status = "installed"
        )
        coEvery { repository.countActiveInstalledFiles(5L) } returns 0
        every { fileService.exists(any()) } returns true
        every { fileService.mkdirs(any()) } returns true
        every { fileService.writeFile(any(), any()) } returns true

        val backupSource = File(tempFolder.root, "backup_conflict.dat")
        backupSource.writeText("original content")
        val backupFis = FileInputStream(backupSource)
        val mockPfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { mockPfd.fileDescriptor } returns backupFis.fd
        every { fileService.openFileForRead(any()) } returns mockPfd

        val result = useCase.execute(1L, 0)

        backupFis.close()

        assertTrue(result.isSuccess)
        coVerify { repository.markInstalledFilesSuperseded(listOf(7L), 100L) }
        coVerify {
            repository.updateInstallation(match {
                it.id == 5L && it.status == com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus.SUPERSEDED
            })
        }
    }

    // --- Helpers ---

    private fun createScript(
        id: Long = 1L,
        name: String = "Test Script",
        storagePath: String = tempFolder.root.absolutePath
    ) = SkinScript(id = id, name = name, storagePath = storagePath)

    private fun createScriptWithEmptyAssets(): SkinScript {
        val scriptDir = tempFolder.newFolder("script_empty")
        val assetsDir = File(scriptDir, ML_ASSETS_REL)
        assetsDir.mkdirs()
        return createScript(storagePath = scriptDir.absolutePath)
    }

    private fun createScriptWithAssets(vararg files: Pair<String, String>): SkinScript {
        val scriptDir = tempFolder.newFolder("script_${System.nanoTime()}")
        val assetsDir = File(scriptDir, ML_ASSETS_REL)
        assetsDir.mkdirs()
        files.forEach { (path, content) ->
            val file = File(assetsDir, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        return createScript(storagePath = scriptDir.absolutePath)
    }
}
