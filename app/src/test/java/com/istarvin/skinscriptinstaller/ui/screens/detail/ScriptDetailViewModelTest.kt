package com.istarvin.skinscriptinstaller.ui.screens.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.db.query.HeroInstallationConflict
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.domain.ClassifyScriptUseCase
import com.istarvin.skinscriptinstaller.domain.ImportConflictResolutionChoice
import com.istarvin.skinscriptinstaller.domain.ImportConflictScriptRef
import com.istarvin.skinscriptinstaller.domain.ImportFileConflict
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.ImportedScriptPayload
import com.istarvin.skinscriptinstaller.domain.InstallProgress
import com.istarvin.skinscriptinstaller.domain.InstallScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.domain.UserSelectionManager
import com.istarvin.skinscriptinstaller.domain.VerifyInstalledScriptsUseCase
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private lateinit var userSelectionManager: UserSelectionManager
    private lateinit var importScriptUseCase: ImportScriptUseCase
    private lateinit var installScriptUseCase: InstallScriptUseCase
    private lateinit var restoreScriptUseCase: RestoreScriptUseCase
    private lateinit var classifyScriptUseCase: ClassifyScriptUseCase
    private lateinit var verifyInstalledScriptsUseCase: VerifyInstalledScriptsUseCase
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
        importScriptUseCase = mockk(relaxed = true)
        installScriptUseCase = mockk(relaxed = true)
        restoreScriptUseCase = mockk(relaxed = true)
        classifyScriptUseCase = mockk(relaxed = true)
        verifyInstalledScriptsUseCase = mockk(relaxed = true)
        shizukuManager = mockk(relaxed = true)

        every { activeUserStore.activeUserId } returns activeUserIdFlow
        every { shizukuManager.fileService } returns fileServiceFlow
        every { shizukuManager.isServiceBound } returns isServiceBoundFlow
        every { installScriptUseCase.progress } returns installProgressFlow
        every { restoreScriptUseCase.progress } returns restoreProgressFlow
        every { repository.getAllHeroes() } returns flowOf(emptyList())
        every { repository.observeLatestInstallation(any(), any()) } returns flowOf(null)
        coEvery { repository.getActiveHeroInstallationConflicts(any(), any(), any()) } returns emptyList()

        userSelectionManager = UserSelectionManager(activeUserStore, shizukuManager)
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
            savedStateHandle, repository, userSelectionManager,
            importScriptUseCase, installScriptUseCase, restoreScriptUseCase,
            classifyScriptUseCase, verifyInstalledScriptsUseCase, shizukuManager
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
        val script = SkinScript(
            id = 1L,
            name = "Test",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        coEvery { repository.getScriptById(1L) } returns script

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = InstallationStatus.INSTALLED)
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)
        coEvery { installScriptUseCase.execute(1L, 0) } answers { Result.success(installation) }

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        coVerify { installScriptUseCase.execute(1L, 0) }
        assertEquals(installation, vm.installation.value)
    }

    @Test
    fun `verifyCurrentInstallation triggers targeted verification for selected user`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        vm.verifyCurrentInstallation()
        advanceUntilIdle()

        coVerify { verifyInstalledScriptsUseCase.execute(1L, 0) }
    }

    @Test
    fun `refreshInstallation runs targeted verification and clears refreshing state`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val vm = createViewModel()
        advanceUntilIdle()

        vm.refreshInstallation()
        advanceUntilIdle()

        coVerify { verifyInstalledScriptsUseCase.execute(1L, 0) }
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun `install blocks unclassified scripts`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        coEvery { repository.getScriptById(1L) } returns script

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertEquals("Classify this script before installing", vm.error.value)
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
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
        val script = SkinScript(
            id = 1L,
            name = "Test",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
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
    fun `install with hero conflict opens warning and does not install`() = runTest {
        val script = SkinScript(
            id = 1L,
            name = "New Miya",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        val conflicts = listOf(
            HeroInstallationConflict(
                installationId = 20L,
                scriptId = 2L,
                scriptName = "Old Miya"
            )
        )
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getHeroById(1L) } returns Hero(id = 1L, name = "Miya")
        coEvery { repository.getActiveHeroInstallationConflicts(1L, 0, 1L) } returns conflicts

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertEquals("Miya", vm.installConflictWarning.value?.heroName)
        assertEquals("New Miya", vm.installConflictWarning.value?.targetScriptName)
        assertEquals(conflicts, vm.installConflictWarning.value?.conflicts)
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }

    @Test
    fun `dismissInstallConflictWarning clears warning state`() = runTest {
        val script = SkinScript(
            id = 1L,
            name = "New Miya",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getHeroById(1L) } returns Hero(id = 1L, name = "Miya")
        coEvery {
            repository.getActiveHeroInstallationConflicts(1L, 0, 1L)
        } returns listOf(
            HeroInstallationConflict(
                installationId = 20L,
                scriptId = 2L,
                scriptName = "Old Miya"
            )
        )

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()
        assertNotNull(vm.installConflictWarning.value)

        vm.dismissInstallConflictWarning()

        assertNull(vm.installConflictWarning.value)
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }

    @Test
    fun `confirmInstallConflictWarning restores conflicts then installs`() = runTest {
        val script = SkinScript(
            id = 1L,
            name = "New Miya",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        val conflicts = listOf(
            HeroInstallationConflict(
                installationId = 20L,
                scriptId = 2L,
                scriptName = "Old Miya"
            ),
            HeroInstallationConflict(
                installationId = 21L,
                scriptId = 3L,
                scriptName = "Older Miya"
            )
        )
        val newInstallation = Installation(id = 30L, scriptId = 1L, userId = 0, status = InstallationStatus.INSTALLED)
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(newInstallation)
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getHeroById(1L) } returns Hero(id = 1L, name = "Miya")
        coEvery { repository.getActiveHeroInstallationConflicts(1L, 0, 1L) } returns conflicts
        coEvery { restoreScriptUseCase.execute(20L) } returns Result.success(Unit)
        coEvery { restoreScriptUseCase.execute(21L) } returns Result.success(Unit)
        coEvery { installScriptUseCase.execute(1L, 0) } returns Result.success(newInstallation)

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()
        vm.confirmInstallConflictWarning()
        advanceUntilIdle()

        coVerify(ordering = Ordering.ORDERED) {
            restoreScriptUseCase.execute(20L)
            restoreScriptUseCase.execute(21L)
            installScriptUseCase.execute(1L, 0)
        }
        assertNull(vm.installConflictWarning.value)
        assertEquals(newInstallation, vm.installation.value)
    }

    @Test
    fun `confirmInstallConflictWarning stops when restore fails`() = runTest {
        val script = SkinScript(
            id = 1L,
            name = "New Miya",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getHeroById(1L) } returns Hero(id = 1L, name = "Miya")
        coEvery {
            repository.getActiveHeroInstallationConflicts(1L, 0, 1L)
        } returns listOf(
            HeroInstallationConflict(
                installationId = 20L,
                scriptId = 2L,
                scriptName = "Old Miya"
            )
        )
        coEvery { restoreScriptUseCase.execute(20L) } returns Result.failure(Exception("Restore error"))
        coEvery { repository.getLatestInstallation(1L, 0) } returns null

        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        fileServiceFlow.value = fileService

        val vm = createViewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()
        vm.confirmInstallConflictWarning()
        advanceUntilIdle()

        assertEquals("Restore error", vm.error.value)
        coVerify { restoreScriptUseCase.execute(20L) }
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }

    @Test
    fun `restore calls RestoreScriptUseCase and reloads installation`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = InstallationStatus.INSTALLED)
        val restored = installation.copy(status = InstallationStatus.RESTORED)
        coEvery { repository.getScriptById(1L) } returns script
        every { repository.observeLatestInstallation(1L, 0) } returns MutableStateFlow(installation)
        // First call during init returns "installed", second call after restore returns "restored"
        coEvery { repository.getLatestInstallation(1L, 0) } returnsMany listOf(installation, restored)
        coEvery { restoreScriptUseCase.execute(10L) } answers { Result.success(Unit) }

        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(InstallationStatus.INSTALLED, vm.installation.value?.status)

        vm.restore()
        advanceUntilIdle()

        coVerify { restoreScriptUseCase.execute(10L) }
        assertEquals(InstallationStatus.RESTORED, vm.installation.value?.status)
    }

    @Test
    fun `reinstall restores first then installs`() = runTest {
        val script = SkinScript(
            id = 1L,
            name = "Test",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = InstallationStatus.INSTALLED)
        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)
        coEvery { restoreScriptUseCase.execute(10L) } answers { Result.success(Unit) }

        val newInstallation = Installation(id = 11L, scriptId = 1L, userId = 0, status = InstallationStatus.INSTALLED)
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
        val script = SkinScript(
            id = 1L,
            name = "Test",
            storagePath = tempFolder.root.absolutePath,
            heroId = 1L
        )
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
    fun `restore blocks replaced installations`() = runTest {
        val script = SkinScript(id = 1L, name = "Test", storagePath = tempFolder.root.absolutePath)
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = InstallationStatus.REPLACED)
        coEvery { repository.getScriptById(1L) } returns script
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.restore()
        advanceUntilIdle()

        assertEquals(
            "Restore is unavailable because Mobile Legends replaced this install",
            vm.error.value
        )
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
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

    @Test
    fun `suggestedHeroName infers hero from script name`() = runTest {
        val script = SkinScript(id = 1L, name = "Miya Legend Pack", storagePath = tempFolder.root.absolutePath)
        val heroes = listOf(Hero(id = 1L, name = "Miya"), Hero(id = 2L, name = "Layla"))
        coEvery { repository.getScriptById(1L) } returns script
        every { repository.getAllHeroes() } returns flowOf(heroes)

        val vm = createViewModel()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.suggestedHeroName.collect {}
        }
        advanceUntilIdle()

        assertEquals("Miya", vm.suggestedHeroName.value)

        collectJob.cancel()
    }

    @Test
    fun `updateScript with file conflicts opens prompt before restore or install`() = runTest {
        val existingStorage = tempFolder.newFolder("existing_storage")
        val script = SkinScript(id = 1L, name = "Test", storagePath = existingStorage.absolutePath)
        val installation = Installation(
            id = 10L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.INSTALLED
        )
        val preparedImport = ImportedScriptPayload(name = "Updated", storagePath = "/prepared")
        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 2L, scriptName = "Existing")
                )
            )
        )

        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)
        coEvery { importScriptUseCase.prepareTreeImport(any()) } returns Result.success(preparedImport)
        coEvery { importScriptUseCase.detectFileConflicts(preparedImport, 1L) } returns
            Result.success(conflicts)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateScript(mockk<Uri>(relaxed = true))
        advanceUntilIdle()

        assertNotNull(vm.updateImportConflictPrompt.value)
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }

    @Test
    fun `confirmUpdateConflictResolution applies choices then runs restore and install`() = runTest {
        val existingStorage = tempFolder.newFolder("existing_storage_confirm")
        val preparedStorage = tempFolder.newFolder("prepared_storage_confirm")
        val script = SkinScript(id = 1L, name = "Test", storagePath = existingStorage.absolutePath)
        val installation = Installation(
            id = 10L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.INSTALLED
        )
        val updatedInstallation = Installation(
            id = 20L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.INSTALLED
        )
        val preparedImport = ImportedScriptPayload(name = "Updated", storagePath = preparedStorage.absolutePath)
        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 2L, scriptName = "Existing")
                )
            )
        )

        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)
        coEvery { importScriptUseCase.prepareTreeImport(any()) } returns Result.success(preparedImport)
        coEvery { importScriptUseCase.detectFileConflicts(preparedImport, 1L) } returns
            Result.success(conflicts)
        coEvery {
            importScriptUseCase.applyConflictChoices(
                preparedImport = preparedImport,
                conflicts = conflicts,
                choices = mapOf(
                    "Art/shared.png" to ImportConflictResolutionChoice.USE_IMPORTED
                )
            )
        } returns Result.success(preparedImport)
        coEvery { restoreScriptUseCase.execute(10L) } returns Result.success(Unit)
        coEvery { installScriptUseCase.execute(1L, 0) } returns Result.success(updatedInstallation)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateScript(mockk<Uri>(relaxed = true))
        advanceUntilIdle()
        vm.updateUpdateConflictChoice(
            relativePath = "Art/shared.png",
            choice = ImportConflictResolutionChoice.USE_IMPORTED
        )
        vm.confirmUpdateConflictResolution()
        advanceUntilIdle()

        assertNull(vm.updateImportConflictPrompt.value)
        coVerify { restoreScriptUseCase.execute(10L) }
        coVerify { installScriptUseCase.execute(1L, 0) }
        coVerify { repository.updateScript(match { it.name == "Updated" }) }
    }

    @Test
    fun `dismissUpdateConflictPrompt cleans prepared import without side effects`() = runTest {
        val existingStorage = tempFolder.newFolder("existing_storage_cancel")
        val script = SkinScript(id = 1L, name = "Test", storagePath = existingStorage.absolutePath)
        val installation = Installation(
            id = 10L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.INSTALLED
        )
        val preparedImport = ImportedScriptPayload(name = "Updated", storagePath = "/prepared")
        val conflicts = listOf(
            ImportFileConflict(
                relativePath = "Art/shared.png",
                existingScripts = listOf(
                    ImportConflictScriptRef(scriptId = 2L, scriptName = "Existing")
                )
            )
        )

        coEvery { repository.getScriptById(1L) } returns script
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        every { repository.observeLatestInstallation(1L, 0) } returns flowOf(installation)
        coEvery { importScriptUseCase.prepareTreeImport(any()) } returns Result.success(preparedImport)
        coEvery { importScriptUseCase.detectFileConflicts(preparedImport, 1L) } returns
            Result.success(conflicts)
        coEvery { importScriptUseCase.cleanupPreparedImport(preparedImport) } just Runs

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateScript(mockk<Uri>(relaxed = true))
        advanceUntilIdle()
        assertNotNull(vm.updateImportConflictPrompt.value)

        vm.dismissUpdateConflictPrompt()
        advanceUntilIdle()

        assertNull(vm.updateImportConflictPrompt.value)
        coVerify { importScriptUseCase.cleanupPreparedImport(preparedImport) }
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }
}
