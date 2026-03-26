package com.istarvin.skinscriptinstaller.domain

import android.content.Context
import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
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
import java.io.File

class RestoreScriptUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: ScriptRepository
    private lateinit var shizukuManager: ShizukuManager
    private lateinit var fileService: IFileService
    private lateinit var fileServiceFlow: MutableStateFlow<IFileService?>
    private lateinit var useCase: RestoreScriptUseCase

    private lateinit var filesDir: File

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

        useCase = RestoreScriptUseCase(context, repository, shizukuManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(ParcelFileDescriptor::class)
    }

    @Test
    fun `execute returns failure when installation not found`() = runTest {
        coEvery { repository.getInstallationById(1L) } returns null

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("Installation not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when installation already restored`() = runTest {
        val installation = createInstallation(status = "restored")
        coEvery { repository.getInstallationById(1L) } returns installation

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("Installation already restored", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when Shizuku service unavailable`() = runTest {
        val installation = createInstallation()
        coEvery { repository.getInstallationById(1L) } returns installation
        fileServiceFlow.value = null

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("Shizuku file service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure when no installed files`() = runTest {
        val installation = createInstallation()
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns emptyList()

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("No installed files to restore", result.exceptionOrNull()?.message)
    }

    @Test
    fun `execute returns failure on mismatched user path`() = runTest {
        val installation = createInstallation(userId = 0)
        val mismatchedFile = InstalledFile(
            id = 1L,
            installationId = 1L,
            destPath = "/storage/emulated/10/Android/data/com.mobile.legends/files/file.txt",
            wasOverwrite = false
        )
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns listOf(mismatchedFile)

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("mismatched user") == true)
    }

    @Test
    fun `execute deletes newly added files`() = runTest {
        val installation = createInstallation()
        val newFile = InstalledFile(
            id = 1L,
            installationId = 1L,
            destPath = "/storage/emulated/0/Android/data/com.mobile.legends/files/new.png",
            wasOverwrite = false
        )
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns listOf(newFile)
        every { fileService.deleteFile(any()) } returns true

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        verify { fileService.deleteFile(newFile.destPath) }
        verify(exactly = 0) { fileService.writeFile(any(), any()) }
    }

    @Test
    fun `execute restores backed-up files`() = runTest {
        val installation = createInstallation()
        val backupFile = File(filesDir, "backups/1/Art/texture.png")
        backupFile.parentFile?.mkdirs()
        backupFile.writeText("original data")

        val overwrittenFile = InstalledFile(
            id = 1L,
            installationId = 1L,
            destPath = "/storage/emulated/0/Android/data/com.mobile.legends/files/Art/texture.png",
            wasOverwrite = true,
            backupPath = backupFile.absolutePath
        )
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns listOf(overwrittenFile)
        every { fileService.writeFile(any(), any()) } returns true

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        verify { fileService.writeFile(any(), overwrittenFile.destPath) }
        verify(exactly = 0) { fileService.deleteFile(any()) }
    }

    @Test
    fun `execute updates installation status to restored`() = runTest {
        val installation = createInstallation()
        val file = createInstalledFile()
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns listOf(file)
        every { fileService.deleteFile(any()) } returns true

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        coVerify {
            repository.updateInstallation(match {
                it.status == "restored" && it.restoredAt != null
            })
        }
    }

    @Test
    fun `execute cleans up backup directory`() = runTest {
        val installation = createInstallation()
        val backupDir = File(filesDir, "backups/1")
        backupDir.mkdirs()
        File(backupDir, "somefile.txt").writeText("backup data")

        val file = createInstalledFile()
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns listOf(file)
        every { fileService.deleteFile(any()) } returns true

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        assertFalse(backupDir.exists())
    }

    @Test
    fun `execute emits progress and completion`() = runTest {
        val installation = createInstallation()
        val files = listOf(
            createInstalledFile(id = 1L, fileName = "file1.png"),
            createInstalledFile(id = 2L, fileName = "file2.png")
        )
        coEvery { repository.getInstallationById(1L) } returns installation
        coEvery { repository.getInstalledFilesByInstallation(1L) } returns files
        every { fileService.deleteFile(any()) } returns true

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
        val progress = useCase.progress.value
        assertNotNull(progress)
        assertTrue(progress!!.isComplete)
        assertEquals(2, progress.total)
    }

    @Test
    fun `execute resets progress on failure`() = runTest {
        coEvery { repository.getInstallationById(1L) } throws RuntimeException("DB error")

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertNull(useCase.progress.value)
    }

    @Test
    fun `resetProgress clears progress state`() {
        useCase.resetProgress()
        assertNull(useCase.progress.value)
    }

    // --- Helpers ---

    private fun createInstallation(
        id: Long = 1L,
        scriptId: Long = 1L,
        userId: Int = 0,
        status: String = "installed"
    ) = Installation(
        id = id,
        scriptId = scriptId,
        userId = userId,
        status = status
    )

    private fun createInstalledFile(
        id: Long = 1L,
        installationId: Long = 1L,
        fileName: String = "file.png",
        wasOverwrite: Boolean = false,
        backupPath: String? = null
    ) = InstalledFile(
        id = id,
        installationId = installationId,
        destPath = "/storage/emulated/0/Android/data/com.mobile.legends/files/$fileName",
        wasOverwrite = wasOverwrite,
        backupPath = backupPath
    )
}
