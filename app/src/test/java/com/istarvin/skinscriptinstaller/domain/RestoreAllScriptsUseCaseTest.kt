package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.db.query.LatestInstalledScript
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestoreAllScriptsUseCaseTest {

    private lateinit var repository: ScriptRepository
    private lateinit var restoreScriptUseCase: RestoreScriptUseCase
    private lateinit var useCase: RestoreAllScriptsUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        restoreScriptUseCase = mockk(relaxed = true)
        useCase = RestoreAllScriptsUseCase(repository, restoreScriptUseCase)
    }

    @Test
    fun `execute restores all eligible installations in sequence`() = runTest {
        coEvery { repository.getLatestInstalledScriptsByUserId(0) } returns listOf(
            LatestInstalledScript(installationId = 11L, scriptId = 1L, scriptName = "Miya Epic"),
            LatestInstalledScript(installationId = 12L, scriptId = 2L, scriptName = "Layla Basic")
        )
        coEvery { restoreScriptUseCase.execute(11L) } returns Result.success(Unit)
        coEvery { restoreScriptUseCase.execute(12L) } returns Result.success(Unit)

        val result = useCase.execute(0)

        assertEquals(2, result.totalCandidates)
        assertEquals(2, result.restoredCount)
        assertTrue(result.failures.isEmpty())
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            restoreScriptUseCase.execute(11L)
            restoreScriptUseCase.execute(12L)
        }
    }

    @Test
    fun `execute continues after one restore fails and returns partial result`() = runTest {
        coEvery { repository.getLatestInstalledScriptsByUserId(0) } returns listOf(
            LatestInstalledScript(installationId = 11L, scriptId = 1L, scriptName = "Miya Epic"),
            LatestInstalledScript(installationId = 12L, scriptId = 2L, scriptName = "Layla Basic")
        )
        coEvery { restoreScriptUseCase.execute(11L) } returns Result.success(Unit)
        coEvery { restoreScriptUseCase.execute(12L) } returns Result.failure(Exception("Disk error"))

        val result = useCase.execute(0)

        assertEquals(2, result.totalCandidates)
        assertEquals(1, result.restoredCount)
        assertEquals(1, result.failures.size)
        assertEquals("Layla Basic", result.failures.single().scriptName)
        assertEquals("Disk error", result.failures.single().message)
    }

    @Test
    fun `execute returns empty result when nothing is restorable`() = runTest {
        coEvery { repository.getLatestInstalledScriptsByUserId(0) } returns emptyList()

        val result = useCase.execute(0)

        assertEquals(0, result.totalCandidates)
        assertEquals(0, result.restoredCount)
        assertTrue(result.failures.isEmpty())
        coVerify(exactly = 0) { restoreScriptUseCase.execute(any()) }
    }
}
