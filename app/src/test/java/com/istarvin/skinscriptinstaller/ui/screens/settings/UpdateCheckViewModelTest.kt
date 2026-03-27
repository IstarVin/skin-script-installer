package com.istarvin.skinscriptinstaller.ui.screens.settings

import app.cash.turbine.test
import com.istarvin.skinscriptinstaller.data.update.AppUpdateManager
import com.istarvin.skinscriptinstaller.domain.CheckForUpdateUseCase
import com.istarvin.skinscriptinstaller.domain.ReleaseInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
    private lateinit var appUpdateManager: AppUpdateManager

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        checkForUpdateUseCase = mockk()
        appUpdateManager = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startUpdate downloads apk and emits install event when permission is granted`() = runTest {
        val releaseInfo = ReleaseInfo(
            version = "1.3.9",
            releaseNotes = "Bug fixes",
            releaseUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.9",
            apkUrl = "https://github.com/IstarVin/skin-script-installer/releases/download/v1.3.9/app.apk"
        )
        val apkUri = mockk<android.net.Uri>()
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(releaseInfo)
        coEvery { appUpdateManager.downloadUpdate(releaseInfo.apkUrl!!, releaseInfo.version) } returns Result.success(apkUri)
        every { appUpdateManager.canRequestPackageInstalls() } returns true

        val viewModel = UpdateCheckViewModel(checkForUpdateUseCase, appUpdateManager)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.startUpdate()
            advanceUntilIdle()

            assertEquals(UpdateEvent.LaunchInstaller(apkUri), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(UpdateState.Idle, viewModel.updateState.value)
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

        val viewModel = UpdateCheckViewModel(checkForUpdateUseCase, appUpdateManager)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.startUpdate()
            advanceUntilIdle()

            assertEquals(UpdateEvent.OpenReleasePage(releaseInfo.releaseUrl), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { appUpdateManager.downloadUpdate(any(), any()) }
        assertEquals(UpdateState.Idle, viewModel.updateState.value)
    }

    @Test
    fun `resumeInstallAfterPermissionCheck launches installer after permission is granted`() = runTest {
        val releaseInfo = ReleaseInfo(
            version = "1.3.9",
            releaseNotes = "Bug fixes",
            releaseUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.9",
            apkUrl = "https://github.com/IstarVin/skin-script-installer/releases/download/v1.3.9/app.apk"
        )
        val apkUri = mockk<android.net.Uri>()
        coEvery { checkForUpdateUseCase.execute() } returns Result.success(releaseInfo)
        coEvery { appUpdateManager.downloadUpdate(releaseInfo.apkUrl!!, releaseInfo.version) } returns Result.success(apkUri)
        every { appUpdateManager.canRequestPackageInstalls() } returnsMany listOf(false, true)

        val viewModel = UpdateCheckViewModel(checkForUpdateUseCase, appUpdateManager)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.startUpdate()
            advanceUntilIdle()
            assertEquals(UpdateEvent.OpenUnknownAppSourcesSettings, awaitItem())

            viewModel.resumeInstallAfterPermissionCheck()
            advanceUntilIdle()
            assertEquals(UpdateEvent.LaunchInstaller(apkUri), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(UpdateState.Idle, viewModel.updateState.value)
    }
}
