package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.network.api.GitHubApiService
import com.istarvin.skinscriptinstaller.data.network.dto.GitHubReleaseAssetDto
import com.istarvin.skinscriptinstaller.data.network.dto.GitHubReleaseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CheckForUpdateUseCaseTest {

    private lateinit var gitHubApiService: GitHubApiService
    private lateinit var useCase: CheckForUpdateUseCase

    @Before
    fun setUp() {
        gitHubApiService = mockk()
        useCase = CheckForUpdateUseCase(gitHubApiService)
    }

    @Test
    fun `execute returns newer release with apk asset url`() = runTest {
        coEvery { gitHubApiService.getLatestRelease() } returns GitHubReleaseDto(
            tagName = "v1.3.9",
            htmlUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.9",
            body = "Bug fixes",
            assets = listOf(
                GitHubReleaseAssetDto(
                    name = "SkinScriptInstaller-1.3.9.apk",
                    contentType = "application/vnd.android.package-archive",
                    browserDownloadUrl = "https://github.com/IstarVin/skin-script-installer/releases/download/v1.3.9/app.apk"
                )
            )
        )

        val result = useCase.execute()
        val releaseInfo = result.getOrNull()

        assertNotNull(releaseInfo)
        assertEquals("1.3.9", releaseInfo?.version)
        assertEquals(
            "https://github.com/IstarVin/skin-script-installer/releases/download/v1.3.9/app.apk",
            releaseInfo?.apkUrl
        )
    }

    @Test
    fun `execute returns null when release is not newer`() = runTest {
        coEvery { gitHubApiService.getLatestRelease() } returns GitHubReleaseDto(
            tagName = "v1.3.8",
            htmlUrl = "https://github.com/IstarVin/skin-script-installer/releases/tag/v1.3.8",
            body = "Current version"
        )

        val result = useCase.execute()

        assertNull(result.getOrNull())
    }
}
