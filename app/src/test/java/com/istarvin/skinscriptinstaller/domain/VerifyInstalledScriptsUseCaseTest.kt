package com.istarvin.skinscriptinstaller.domain

import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VerifyInstalledScriptsUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: ScriptRepository
    private lateinit var shizukuManager: ShizukuManager
    private lateinit var fileService: IFileService
    private lateinit var fileServiceFlow: MutableStateFlow<IFileService?>
    private lateinit var useCase: VerifyInstalledScriptsUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        shizukuManager = mockk(relaxed = true)
        fileService = mockk(relaxed = true)

        fileServiceFlow = MutableStateFlow(fileService)
        every { shizukuManager.fileService } returns fileServiceFlow

        useCase = VerifyInstalledScriptsUseCase(repository, shizukuManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `execute marks installation replaced when installed file is missing`() = runTest {
        val script = createScriptWithAssets("Art/hero.png" to "expected")
        val installation = Installation(
            id = 5L,
            scriptId = script.id,
            userId = 0,
            status = InstallationStatus.INSTALLED
        )
        val installedFile = InstalledFile(
            installationId = installation.id,
            destPath = "${buildMlAssetsRoot(0)}/Art/hero.png",
            wasOverwrite = false
        )

        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        every { fileService.exists(installedFile.destPath) } returns false
        coEvery { repository.getLatestInstallationsOnce(0) } returns listOf(installation)
        coEvery { repository.getScriptById(script.id) } returns script
        coEvery { repository.getInstalledFilesByInstallation(installation.id) } returns listOf(installedFile)

        val didRun = useCase.execute()

        assertTrue(didRun)
        coVerify {
            repository.updateInstallation(match {
                it.id == installation.id && it.status == InstallationStatus.REPLACED
            })
        }
    }

    @Test
    fun `execute keeps installation installed when bytes still match`() = runTest {
        val script = createScriptWithAssets("Art/hero.png" to "expected")
        val installation = Installation(
            id = 5L,
            scriptId = script.id,
            userId = 0,
            status = InstallationStatus.REPLACED
        )
        val installedFile = InstalledFile(
            installationId = installation.id,
            destPath = "${buildMlAssetsRoot(0)}/Art/hero.png",
            wasOverwrite = false
        )
        val remoteFile = tempFolder.newFile("remote_hero.png")
        remoteFile.writeText("expected")
        val remoteDescriptor = ParcelFileDescriptor.open(
            remoteFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        every { fileService.listEligibleMlUserIds() } returns intArrayOf(0)
        every { fileService.exists(installedFile.destPath) } returns true
        every { fileService.openFileForRead(installedFile.destPath) } returns remoteDescriptor
        coEvery { repository.getLatestInstallationsOnce(0) } returns listOf(installation)
        coEvery { repository.getScriptById(script.id) } returns script
        coEvery { repository.getInstalledFilesByInstallation(installation.id) } returns listOf(installedFile)

        val didRun = useCase.execute()

        assertTrue(didRun)
        coVerify {
            repository.updateInstallation(match {
                it.id == installation.id && it.status == InstallationStatus.INSTALLED
            })
        }
    }

    @Test
    fun `execute returns false when service is unavailable`() = runTest {
        fileServiceFlow.value = null

        val didRun = useCase.execute()

        assertFalse(didRun)
        coVerify(exactly = 0) { repository.updateInstallation(any()) }
    }

    private fun createScriptWithAssets(vararg files: Pair<String, String>): SkinScript {
        val scriptDir = tempFolder.newFolder("script_${System.nanoTime()}")
        val assetsDir = File(scriptDir, ML_ASSETS_RELATIVE_PATH)
        assetsDir.mkdirs()

        files.forEach { (relativePath, content) ->
            val file = File(assetsDir, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        return SkinScript(id = 1L, name = "Test Script", storagePath = scriptDir.absolutePath)
    }
}