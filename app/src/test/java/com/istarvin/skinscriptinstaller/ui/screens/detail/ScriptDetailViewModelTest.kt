package com.istarvin.skinscriptinstaller.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.domain.InstallProgress
import com.istarvin.skinscriptinstaller.domain.InstallScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptDetailViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var repository: ScriptRepository
    private lateinit var activeUserStore: ActiveUserStore
    private lateinit var installScriptUseCase: InstallScriptUseCase
    private lateinit var restoreScriptUseCase: RestoreScriptUseCase
    private lateinit var shizukuManager: ShizukuManager

    private val activeUserIdFlow = MutableStateFlow(0)
    private val fileServiceFlow = MutableStateFlow<IFileService?>(null)
    private val installProgressFlow = MutableStateFlow<InstallProgress?>(null)
    private val restoreProgressFlow = MutableStateFlow<InstallProgress?>(null)
    private val isServiceBoundFlow = MutableStateFlow(false)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = SavedStateHandle(mapOf("scriptId" to 1L))
        repository = mockk(relaxed = true)
        activeUserStore = mockk(relaxed = true)
        installScriptUseCase = mockk(relaxed = true)
        restoreScriptUseCase = mockk(relaxed = true)
        shizukuManager = mockk(relaxed = true)

        every { activeUserStore.activeUserId } returns activeUserIdFlow
        every { shizukuManager.fileService } returns fileServiceFlow
        every { shizukuManager.isServiceBound } returns isServiceBoundFlow
        every { installScriptUseCase.progress } returns installProgressFlow
        every { restoreScriptUseCase.progress } returns restoreProgressFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(scriptId: Long = 1L): ScriptDetailViewModel {
        if (scriptId != 1L) {
            savedStateHandle = SavedStateHandle(mapOf("scriptId" to scriptId))
        }
        return ScriptDetailViewModel(
            savedStateHandle, repository, activeUserStore,
            installScriptUseCase, restoreScriptUseCase, shizukuManager
        )
    }

    @Test
    fun `loads script on init`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(script, vm.script.value)
    }

    @Test
    fun `file tree sorts directories first then by name`() = runTest {
        val storageDir = tempFolder.newFolder("script_storage")
        File(storageDir, "zebra.txt").writeText("z")
        File(storageDir, "apple.txt").writeText("a")
        val subDir = File(storageDir, "beta_dir")
        subDir.mkdirs()
        File(subDir, "child.txt").writeText("c")
        val subDir2 = File(storageDir, "alpha_dir")
        subDir2.mkdirs()

        val script = SkinScript(id = 1L, name = "Test", storagePath = storageDir.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        val tree = vm.fileTree.value
        assertTrue(tree.isNotEmpty())
        // Directories should come first
        assertTrue(tree[0].isDirectory)
        assertTrue(tree[1].isDirectory)
        assertFalse(tree[2].isDirectory)
        // Directories sorted by name
        assertEquals("alpha_dir", tree[0].name)
        assertEquals("beta_dir", tree[1].name)
        // Files sorted by name
        assertEquals("apple.txt", tree[2].name)
        assertEquals("zebra.txt", tree[3].name)
    }

    @Test
    fun `file tree auto-expands path to Art directory`() = runTest {
        val storageDir = tempFolder.newFolder("script_storage_art")
        val artPath = File(storageDir, "Android/data/com.mobile.legends/files/dragon2017/assets/Art")
        artPath.mkdirs()
        File(artPath, "texture.png").writeText("tex")

        val script = SkinScript(id = 1L, name = "Test", storagePath = storageDir.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        val expanded = vm.expandedDirectoryIds.value
        // Should expand Android, data, com.mobile.legends, files, dragon2017, assets
        // but NOT Art itself (dropLast(1) in collectDefaultExpandedDirectoryIds)
        assertTrue("Expected some expanded directories", expanded.isNotEmpty())
        // Art directory itself should NOT be in expanded set (only path TO Art)
        assertFalse(expanded.any { it.endsWith("/Art") })
    }

    @Test
    fun `toggleDirectory adds and removes from expandedDirectoryIds`() = runTest {
        val storageDir = tempFolder.newFolder("script_toggle")
        File(storageDir, "dir1").mkdirs()

        val script = SkinScript(id = 1L, name = "Test", storagePath = storageDir.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        val dirId = vm.fileTree.value.first { it.isDirectory }.id

        // Toggle on
        vm.toggleDirectory(dirId)
        assertTrue(vm.expandedDirectoryIds.value.contains(dirId))

        // Toggle off
        vm.toggleDirectory(dirId)
        assertFalse(vm.expandedDirectoryIds.value.contains(dirId))
    }

    @Test
    fun `install calls InstallScriptUseCase with correct scriptId and userId`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = "installed")
        coEvery { installScriptUseCase.execute(1L, 0) } answers { Result.success(installation) }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        coVerify { installScriptUseCase.execute(1L, 0) }
        assertEquals(installation, vm.installation.value)
    }

    @Test
    fun `install sets error when no eligible users`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        // No file service = no eligible users
        fileServiceFlow.value = null

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertTrue(vm.error.value!!.contains("No Mobile Legends user"))
    }

    @Test
    fun `install sets error on failure`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        coEvery { installScriptUseCase.execute(1L, 0) } answers { Result.failure(Exception("Install error")) }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertEquals("Install error", vm.error.value)
    }

    @Test
    fun `restore calls RestoreScriptUseCase and reloads installation`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = "installed")
        val restored = installation.copy(status = "restored")
        coEvery { repository.getScriptById(1L) } returns script
        // First call during init returns "installed", second call after restore returns "restored"
        coEvery { repository.getLatestInstallation(1L, 0) } returnsMany listOf(installation, restored)
        coEvery { restoreScriptUseCase.execute(10L) } answers { Result.success(Unit) }

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals("installed", vm.installation.value?.status)

        vm.restore()
        advanceUntilIdle()

        coVerify { restoreScriptUseCase.execute(10L) }
        assertEquals("restored", vm.installation.value?.status)
    }

    @Test
    fun `reinstall restores first then installs`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = "installed")
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        coEvery { restoreScriptUseCase.execute(10L) } answers { Result.success(Unit) }

        val newInstallation = Installation(id = 11L, scriptId = 1L, userId = 0, status = "installed")
        coEvery { installScriptUseCase.execute(1L, 0) } answers { Result.success(newInstallation) }

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.reinstall()
        advanceUntilIdle()

        coVerify(ordering = Ordering.ORDERED) {
            restoreScriptUseCase.execute(10L)
            installScriptUseCase.execute(1L, 0)
        }
    }

    @Test
    fun `reinstall fails if no installed version`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns null

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.reinstall()
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertTrue(vm.error.value!!.contains("No installed version"))
    }

    @Test
    fun `installForUser rejects ineligible user`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script
        // No file service = no eligible users
        fileServiceFlow.value = null

        val vm = createViewModel()
        advanceUntilIdle()

        vm.installForUser(99)
        advanceUntilIdle()

        assertEquals("Selected user is not eligible", vm.error.value)
    }

    @Test
    fun `clearError resets error state`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script
        fileServiceFlow.value = null

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()
        assertNotNull(vm.error.value)

        vm.clearError()
        assertNull(vm.error.value)
    }
}
