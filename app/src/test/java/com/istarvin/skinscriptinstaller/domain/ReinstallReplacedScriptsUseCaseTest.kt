package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.db.query.FileOwnershipConflict
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReinstallReplacedScriptsUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: ScriptRepository
    private lateinit var restoreScriptUseCase: RestoreScriptUseCase
    private lateinit var installScriptUseCase: InstallScriptUseCase
    private lateinit var useCase: ReinstallReplacedScriptsUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        restoreScriptUseCase = mockk(relaxed = true)
        installScriptUseCase = mockk(relaxed = true)
        useCase = ReinstallReplacedScriptsUseCase(
            repository = repository,
            restoreScriptUseCase = restoreScriptUseCase,
            installScriptUseCase = installScriptUseCase
        )
    }

    @Test
    fun `execute skips conflicting replaced script without restoring`() = runTest {
        val installation = Installation(
            id = 10L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.REPLACED
        )
        val script = createInstallableScript(id = 1L, name = "Test Script")
        val conflictPath = "${buildMlAssetsRoot(0)}/Art/hero.png"

        coEvery { repository.getLatestInstallationsOnce(0) } returns listOf(installation)
        coEvery { repository.getScriptById(1L) } returns script
        coEvery {
            repository.getActiveFileOwnershipConflicts(
                any(),
                0,
                1L,
                setOf(10L)
            )
        } returns listOf(
            FileOwnershipConflict(
                installedFileId = 3L,
                installationId = 22L,
                scriptId = 2L,
                scriptName = "Other Script",
                destPath = conflictPath,
                installedAt = 1L
            )
        )

        val result = useCase.execute(0)

        assertEquals(1, result.totalCandidates)
        assertEquals(0, result.reinstalledCount)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.single().message.contains("Other Script"))
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
        coVerify(exactly = 0) { installScriptUseCase.execute(any(), any()) }
    }

    @Test
    fun `execute restores and installs conflict free replaced script`() = runTest {
        val installation = Installation(
            id = 10L,
            scriptId = 1L,
            userId = 0,
            status = InstallationStatus.REPLACED
        )
        val script = createInstallableScript(id = 1L, name = "Test Script")

        coEvery { repository.getLatestInstallationsOnce(0) } returns listOf(installation)
        coEvery { repository.getScriptById(1L) } returns script
        coEvery {
            repository.getActiveFileOwnershipConflicts(
                any(),
                0,
                1L,
                setOf(10L)
            )
        } returns emptyList()
        coEvery { restoreScriptUseCase.execute(10L) } returns Result.success(Unit)
        coEvery { installScriptUseCase.execute(1L, 0) } returns Result.success(installation)

        val result = useCase.execute(0)

        assertEquals(1, result.totalCandidates)
        assertEquals(1, result.reinstalledCount)
        assertTrue(result.failures.isEmpty())
        coVerify(ordering = Ordering.ORDERED) {
            restoreScriptUseCase.execute(10L)
            installScriptUseCase.execute(1L, 0)
        }
    }

    private fun createInstallableScript(id: Long, name: String): SkinScript {
        val storageDir = tempFolder.newFolder("script_${System.nanoTime()}")
        val assetsDir = File(
            storageDir,
            "Android/data/com.mobile.legends/files/dragon2017/assets/Art"
        )
        assetsDir.mkdirs()
        File(assetsDir, "hero.png").writeText("data")
        return SkinScript(
            id = id,
            name = name,
            storagePath = storageDir.absolutePath,
            heroId = 1L
        )
    }
}
