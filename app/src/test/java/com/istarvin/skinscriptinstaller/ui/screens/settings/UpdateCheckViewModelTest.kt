package com.istarvin.skinscriptinstaller.ui.screens.settings

import app.cash.turbine.test
import com.istarvin.skinscriptinstaller.data.update.UpdateDownloadStarter
import com.istarvin.skinscriptinstaller.data.update.UpdateDownloadTracker
import com.istarvin.skinscriptinstaller.domain.CheckForUpdateUseCase
import com.istarvin.skinscriptinstaller.domain.ReleaseInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckViewModelTest {

    private lateinit var checkForUpdateUseCase: CheckForUpdateUseCase
    private lateinit var updateDownloadStarter: UpdateDownloadStarter
    private lateinit var updateDownloadTracker: UpdateDownloadTracker

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkForUpdateUseCase = mockk()
        updateDownloadStarter = mockk()
        updateDownloadTracker = UpdateDownloadTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startUpdate begins a background download when apk asset is available`() = runTest {
        val releaseInfo = ReleaseInfo(
            version = "1.3.9",
            releaseNotes = "Bug fixes",
            releaseUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.9",
            apkUrl = "https://github.com/IstarVin/skin-script-installer/releases/download/v1.3.9/app.apk"
        )
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(releaseInfo)
        every {
            updateDownloadStarter.startDownload(releaseInfo.apkUrl!!, releaseInfo.version)
        } returns Result.success(Unit)

        val viewModel = UpdateCheckViewModel(
            checkForUpdateUseCase,
            updateDownloadStarter,
            updateDownloadTracker
        )
        advanceUntilIdle()

        viewModel.startUpdate()
        advanceUntilIdle()

        verify(exactly = 1) {
            updateDownloadStarter.startDownload(releaseInfo.apkUrl!!, releaseInfo.version)
        }
        assertEquals(
            UpdateState.Downloading(version = releaseInfo.version),
            viewModel.updateState.value
        )
    }

    @Test
    fun `startUpdate opens release page when no apk asset is available`() = runTest {
        val releaseInfo = ReleaseInfo(
            version = "1.3.9",
            releaseNotes = "Bug fixes",
            releaseUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.9",
            apkUrl = null
        )
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(releaseInfo)

        val viewModel = UpdateCheckViewModel(
            checkForUpdateUseCase,
            updateDownloadStarter,
            updateDownloadTracker
        )
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.startUpdate()
            advanceUntilIdle()

            assertEquals(UpdateEvent.OpenReleasePage(releaseInfo.releaseUrl), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { updateDownloadStarter.startDownload(any(), any()) }
        assertEquals(UpdateState.Idle, viewModel.updateState.value)
    }

    @Test
    fun `download tracker completion is reflected in update state`() = runTest {
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(null)

        val viewModel = UpdateCheckViewModel(
            checkForUpdateUseCase,
            updateDownloadStarter,
            updateDownloadTracker
        )
        advanceUntilIdle()

        updateDownloadTracker.markCompleted("1.4.0")
        advanceUntilIdle()

        assertEquals(UpdateState.Downloaded("1.4.0"), viewModel.updateState.value)
    }

    @Test
    fun `download tracker failure is reflected in update state`() = runTest {
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(null)

        val viewModel = UpdateCheckViewModel(
            checkForUpdateUseCase,
            updateDownloadStarter,
            updateDownloadTracker
        )
        advanceUntilIdle()

        updateDownloadTracker.markFailed("1.4.0", "Network down")
        advanceUntilIdle()

        assertEquals(UpdateState.Error("Network down"), viewModel.updateState.value)
    }

    @Test
    fun `notification permission denial surfaces an error`() = runTest {
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(null)

        val viewModel = UpdateCheckViewModel(
            checkForUpdateUseCase,
            updateDownloadStarter,
            updateDownloadTracker
        )
        advanceUntilIdle()

        viewModel.onNotificationPermissionDenied()

        assertEquals(
            UpdateState.Error("Allow notifications to show update download progress"),
            viewModel.updateState.value
        )
    }
}
