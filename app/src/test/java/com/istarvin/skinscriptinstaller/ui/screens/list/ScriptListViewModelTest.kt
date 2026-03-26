package com.istarvin.skinscriptinstaller.ui.screens.list

import android.net.Uri
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.data.user.ActiveUserStore
import com.istarvin.skinscriptinstaller.domain.FetchHeroCatalogUseCase
import com.istarvin.skinscriptinstaller.domain.ImportScriptUseCase
import com.istarvin.skinscriptinstaller.domain.RestoreScriptUseCase
import com.istarvin.skinscriptinstaller.service.InvalidPasswordException
import com.istarvin.skinscriptinstaller.service.PasswordRequiredException
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptListViewModelTest {

    private lateinit var repository: ScriptRepository
    private lateinit var importScriptUseCase: ImportScriptUseCase
    private lateinit var restoreScriptUseCase: RestoreScriptUseCase
    private lateinit var fetchHeroCatalogUseCase: FetchHeroCatalogUseCase
    private lateinit var activeUserStore: ActiveUserStore
    private lateinit var shizukuManager: ShizukuManager

    private val activeUserIdFlow = MutableStateFlow(0)
    private val fileServiceFlow = MutableStateFlow<IFileService?>(null)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ScriptListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        importScriptUseCase = mockk(relaxed = true)
        restoreScriptUseCase = mockk(relaxed = true)
        fetchHeroCatalogUseCase = mockk(relaxed = true)
        activeUserStore = mockk(relaxed = true)
        shizukuManager = mockk(relaxed = true)

        every { activeUserStore.activeUserId } returns activeUserIdFlow
        every { shizukuManager.fileService } returns fileServiceFlow
        every { repository.getAllScripts() } returns flowOf(emptyList())
        every { repository.getAllHeroes() } returns flowOf(emptyList())
        every { repository.getLatestInstallations(any<Int>()) } returns flowOf(emptyList())
        coEvery { fetchHeroCatalogUseCase.execute() } returns Result.success(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ScriptListViewModel(
            repository,
            importScriptUseCase,
            restoreScriptUseCase,
            activeUserStore,
            shizukuManager,
            fetchHeroCatalogUseCase
        )
    }

    @Test
    fun `scriptsWithStatus combines scripts with installations for active user`() = runTest {
        val scripts = listOf(
            SkinScript(id = 1L, name = "Script 1", storagePath = "/path/1"),
            SkinScript(id = 2L, name = "Script 2", storagePath = "/path/2")
        )
        val installations = listOf(
            Installation(id = 10L, scriptId = 1L, userId = 0, status = "installed")
        )
        every { repository.getAllScripts() } returns flowOf(scripts)
        every { repository.getLatestInstallations(0) } returns flowOf(installations)

        createViewModel()

        // Subscribe to trigger stateIn(WhileSubscribed)
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.scriptsWithStatus.collect {}
        }

        val result = viewModel.scriptsWithStatus.value
        assertEquals(2, result.size)
        assertEquals("installed", result[0].status)
        assertEquals("not_installed", result[1].status)

        collectJob.cancel()
    }

    @Test
    fun `heroScriptSections groups scripts by hero and uncategorized by default`() = runTest {
        val heroes = listOf(
            Hero(id = 1L, name = "Miya"),
            Hero(id = 2L, name = "Layla")
        )
        val scripts = listOf(
            SkinScript(id = 1L, name = "Miya Epic", storagePath = "/path/1", heroId = 1L),
            SkinScript(id = 2L, name = "Layla Basic", storagePath = "/path/2", heroId = 2L),
            SkinScript(id = 3L, name = "Mystery", storagePath = "/path/3")
        )

        every { repository.getAllHeroes() } returns flowOf(heroes)
        every { repository.getAllScripts() } returns flowOf(scripts)

        createViewModel()

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.heroScriptSections.collect {}
        }
        advanceUntilIdle()

        val result = viewModel.heroScriptSections.value
        assertEquals(3, result.size)
        assertEquals(listOf("Miya", "Layla", "Uncategorized"), result.map { it.title })
        assertTrue(result.all { it.isExpanded })
        assertEquals(listOf(1L), result[0].skinReplacementSections.single().scripts.map { it.script.id })
        assertEquals(listOf(2L), result[1].skinReplacementSections.single().scripts.map { it.script.id })
        assertEquals(listOf(3L), result[2].flatScripts.map { it.script.id })

        collectJob.cancel()
    }

    @Test
    fun `toggleHeroSection updates only targeted section expansion`() = runTest {
        val heroes = listOf(
            Hero(id = 1L, name = "Miya"),
            Hero(id = 2L, name = "Layla")
        )
        val scripts = listOf(
            SkinScript(id = 1L, name = "Miya Epic", storagePath = "/path/1", heroId = 1L),
            SkinScript(id = 2L, name = "Layla Basic", storagePath = "/path/2", heroId = 2L)
        )

        every { repository.getAllHeroes() } returns flowOf(heroes)
        every { repository.getAllScripts() } returns flowOf(scripts)

        createViewModel()

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.heroScriptSections.collect {}
        }
        advanceUntilIdle()

        viewModel.toggleSection("Miya")
        advanceUntilIdle()

        var result = viewModel.heroScriptSections.value
        assertFalse(result.first { it.key == "Miya" }.isExpanded)
        assertTrue(result.first { it.key == "Layla" }.isExpanded)

        viewModel.toggleSection("Miya")
        advanceUntilIdle()

        result = viewModel.heroScriptSections.value
        assertTrue(result.first { it.key == "Miya" }.isExpanded)

        collectJob.cancel()
    }

    @Test
    fun `importScript calls use case and clears error on success`() = runTest {
        coEvery { importScriptUseCase.execute(any()) } answers {
            Result.success(SkinScript(id = 1L, name = "Test", storagePath = "/path"))
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importScript(uri)
        advanceUntilIdle()

        assertNull(viewModel.importError.value)
        assertFalse(viewModel.isImporting.value)
        coVerify { importScriptUseCase.execute(uri) }
    }

    @Test
    fun `importScript sets error message on failure`() = runTest {
        coEvery { importScriptUseCase.execute(any()) } answers {
            Result.failure(Exception("Import failed"))
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importScript(uri)
        advanceUntilIdle()

        assertEquals("Import failed", viewModel.importError.value)
        assertFalse(viewModel.isImporting.value)
    }

    @Test
    fun `importZip triggers password prompt on PasswordRequiredException`() = runTest {
        coEvery { importScriptUseCase.executeZip(any(), any()) } answers {
            Result.failure(PasswordRequiredException())
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importZip(uri)
        advanceUntilIdle()

        val prompt = viewModel.zipPasswordPrompt.value
        assertNotNull(prompt)
        assertEquals(uri, prompt!!.zipUri)
        assertNull(prompt.errorMessage)
    }

    @Test
    fun `importZip triggers password prompt with error on InvalidPasswordException`() = runTest {
        coEvery { importScriptUseCase.executeZip(any(), any()) } answers {
            Result.failure(InvalidPasswordException())
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importZip(uri)
        advanceUntilIdle()

        val prompt = viewModel.zipPasswordPrompt.value
        assertNotNull(prompt)
        assertEquals("Incorrect password, try again", prompt!!.errorMessage)
    }

    @Test
    fun `importZip sets general error on other exceptions`() = runTest {
        coEvery { importScriptUseCase.executeZip(any(), any()) } answers {
            Result.failure(Exception("General error"))
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importZip(uri)
        advanceUntilIdle()

        assertNull(viewModel.zipPasswordPrompt.value)
        assertEquals("General error", viewModel.importError.value)
    }

    @Test
    fun `retryZipWithPassword retries with password`() = runTest {
        val uri = mockk<Uri>(relaxed = true)

        // First call triggers password prompt, second call succeeds
        var callCount = 0
        coEvery { importScriptUseCase.executeZip(any(), any()) } answers {
            callCount++
            if (callCount == 1) {
                Result.failure(PasswordRequiredException())
            } else {
                Result.success(SkinScript(id = 1L, name = "Test", storagePath = "/path"))
            }
        }

        createViewModel()

        viewModel.importZip(uri)
        advanceUntilIdle()
        assertNotNull(viewModel.zipPasswordPrompt.value)

        viewModel.retryZipWithPassword("mypass")
        advanceUntilIdle()

        assertNull(viewModel.zipPasswordPrompt.value)
    }

    @Test
    fun `dismissZipPasswordPrompt clears prompt`() = runTest {
        coEvery { importScriptUseCase.executeZip(any(), any()) } answers {
            Result.failure(PasswordRequiredException())
        }
        createViewModel()

        val uri = mockk<Uri>(relaxed = true)
        viewModel.importZip(uri)
        advanceUntilIdle()

        assertNotNull(viewModel.zipPasswordPrompt.value)
        viewModel.dismissZipPasswordPrompt()
        assertNull(viewModel.zipPasswordPrompt.value)
    }

    @Test
    fun `deleteScript deletes from DB and file system`() = runTest {
        val script = SkinScript(id = 1L, name = "Script", storagePath = "/tmp/nonexistent")
        createViewModel()

        viewModel.deleteScript(script)
        advanceUntilIdle()

        coVerify { repository.deleteScript(script) }
    }

    @Test
    fun `deleteScript with restoreBeforeDelete restores first then deletes`() = runTest {
        val script = SkinScript(id = 1L, name = "Script", storagePath = "/tmp/nonexistent")
        val installation = Installation(id = 10L, scriptId = 1L, userId = 0, status = "installed")
        coEvery { repository.getLatestInstallation(1L, 0) } returns installation
        coEvery { restoreScriptUseCase.execute(10L) } answers { Result.success(Unit) }

        createViewModel()
        viewModel.deleteScript(script, restoreBeforeDelete = true)
        advanceUntilIdle()

        coVerify(ordering = Ordering.ORDERED) {
            restoreScriptUseCase.execute(10L)
            repository.deleteScript(script)
        }
    }

    @Test
    fun `deleteScript with restoreBeforeDelete fails if no active installation`() = runTest {
        val script = SkinScript(id = 1L, name = "Script", storagePath = "/tmp/nonexistent")
        coEvery { repository.getLatestInstallation(1L, 0) } returns null

        createViewModel()
        viewModel.deleteScript(script, restoreBeforeDelete = true)
        advanceUntilIdle()

        assertEquals("No active installation found to restore", viewModel.importError.value)
        coVerify(exactly = 0) { repository.deleteScript(any()) }
    }

    @Test
    fun `selectActiveUser delegates to ActiveUserStore`() = runTest {
        val fileService = mockk<IFileService>(relaxed = true)
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0, 10)
        fileServiceFlow.value = fileService

        createViewModel()
        advanceUntilIdle()

        viewModel.selectActiveUser(10)

        verify { activeUserStore.setActiveUser(10) }
    }

    @Test
    fun `clearImportError resets error state`() = runTest {
        coEvery { importScriptUseCase.execute(any()) } answers {
            Result.failure(Exception("error"))
        }
        createViewModel()

        viewModel.importScript(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals("error", viewModel.importError.value)

        viewModel.clearImportError()
        assertNull(viewModel.importError.value)
    }
}
