package com.istarvin.skinscriptinstaller.data.repository

import com.istarvin.skinscriptinstaller.data.db.dao.HeroDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.AppDatabase
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.db.query.FileOwnershipConflict
import com.istarvin.skinscriptinstaller.data.db.query.HeroInstallationConflict
import com.istarvin.skinscriptinstaller.data.db.query.LatestInstalledScript
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScriptRepositoryTest {

    private lateinit var skinScriptDao: SkinScriptDao
    private lateinit var installationDao: InstallationDao
    private lateinit var installedFileDao: InstalledFileDao
    private lateinit var heroDao: HeroDao
    private lateinit var skinDao: SkinDao
    private lateinit var appDatabase: AppDatabase
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        skinScriptDao = mockk(relaxed = true)
        installationDao = mockk(relaxed = true)
        installedFileDao = mockk(relaxed = true)
        heroDao = mockk(relaxed = true)
        skinDao = mockk(relaxed = true)
        appDatabase = mockk(relaxed = true)
        repository = ScriptRepository(appDatabase, skinScriptDao, installationDao, installedFileDao, heroDao, skinDao)
    }

    // --- SkinScript delegation ---

    @Test
    fun `getAllScripts delegates to dao`() {
        val scripts = listOf(SkinScript(id = 1L, name = "S1", storagePath = "/p1"))
        every { skinScriptDao.getAll() } returns flowOf(scripts)

        val result = repository.getAllScripts()
        assertNotNull(result)
        verify { skinScriptDao.getAll() }
    }

    @Test
    fun `getScriptById delegates to dao`() = runTest {
        val script = SkinScript(id = 1L, name = "S1", storagePath = "/p1")
        coEvery { skinScriptDao.getById(1L) } returns script

        val result = repository.getScriptById(1L)
        assertEquals(script, result)
        coVerify { skinScriptDao.getById(1L) }
    }

    @Test
    fun `insertScript delegates to dao`() = runTest {
        val script = SkinScript(name = "S1", storagePath = "/p1")
        coEvery { skinScriptDao.insert(script) } returns 1L

        val result = repository.insertScript(script)
        assertEquals(1L, result)
        coVerify { skinScriptDao.insert(script) }
    }

    @Test
    fun `deleteScript delegates to dao`() = runTest {
        val script = SkinScript(id = 1L, name = "S1", storagePath = "/p1")
        coEvery { skinScriptDao.delete(script) } just Runs

        repository.deleteScript(script)
        coVerify { skinScriptDao.delete(script) }
    }

    @Test
    fun `deleteScriptById delegates to dao`() = runTest {
        coEvery { skinScriptDao.deleteById(1L) } just Runs

        repository.deleteScriptById(1L)
        coVerify { skinScriptDao.deleteById(1L) }
    }

    // --- Installation delegation ---

    @Test
    fun `insertInstallation delegates to dao`() = runTest {
        val installation = Installation(scriptId = 1L, userId = 0)
        coEvery { installationDao.insert(installation) } returns 10L

        val result = repository.insertInstallation(installation)
        assertEquals(10L, result)
        coVerify { installationDao.insert(installation) }
    }

    @Test
    fun `updateInstallation delegates to dao`() = runTest {
        val installation = Installation(id = 1L, scriptId = 1L, status = "restored")
        coEvery { installationDao.update(installation) } just Runs

        repository.updateInstallation(installation)
        coVerify { installationDao.update(installation) }
    }

    @Test
    fun `getLatestInstallation by scriptId delegates to dao`() = runTest {
        val installation = Installation(id = 1L, scriptId = 1L)
        coEvery { installationDao.getLatestByScriptId(1L) } returns installation

        val result = repository.getLatestInstallation(1L)
        assertEquals(installation, result)
    }

    @Test
    fun `getLatestInstallation by scriptId and userId delegates to dao`() = runTest {
        val installation = Installation(id = 1L, scriptId = 1L, userId = 10)
        coEvery { installationDao.getLatestByScriptIdAndUserId(1L, 10) } returns installation

        val result = repository.getLatestInstallation(1L, 10)
        assertEquals(installation, result)
    }

    @Test
    fun `getInstallationById delegates to dao`() = runTest {
        val installation = Installation(id = 5L, scriptId = 1L)
        coEvery { installationDao.getById(5L) } returns installation

        val result = repository.getInstallationById(5L)
        assertEquals(installation, result)
    }

    @Test
    fun `getActiveHeroInstallationConflicts delegates to dao`() = runTest {
        val conflicts = listOf(
            HeroInstallationConflict(
                installationId = 11L,
                scriptId = 2L,
                scriptName = "Miya Epic"
            )
        )
        coEvery {
            installationDao.getActiveConflictsByHeroId(
                heroId = 1L,
                userId = 0,
                excludeScriptId = 99L
            )
        } returns conflicts

        val result = repository.getActiveHeroInstallationConflicts(
            heroId = 1L,
            userId = 0,
            excludeScriptId = 99L
        )

        assertEquals(conflicts, result)
        coVerify {
            installationDao.getActiveConflictsByHeroId(
                heroId = 1L,
                userId = 0,
                excludeScriptId = 99L
            )
        }
    }

    @Test
    fun `getLatestInstallations delegates to dao`() {
        val installations = listOf(Installation(id = 1L, scriptId = 1L))
        every { installationDao.getLatestInstallations() } returns flowOf(installations)

        val result = repository.getLatestInstallations()
        assertNotNull(result)
        verify { installationDao.getLatestInstallations() }
    }

    @Test
    fun `getLatestInstallations by userId delegates to dao`() {
        val installations = listOf(Installation(id = 1L, scriptId = 1L, userId = 5))
        every { installationDao.getLatestInstallationsByUserId(5) } returns flowOf(installations)

        val result = repository.getLatestInstallations(5)
        assertNotNull(result)
        verify { installationDao.getLatestInstallationsByUserId(5) }
    }

    @Test
    fun `getLatestInstalledScriptsByUserId delegates to dao`() = runTest {
        val installedScripts = listOf(
            LatestInstalledScript(
                installationId = 10L,
                scriptId = 1L,
                scriptName = "Miya Epic"
            )
        )
        coEvery { installationDao.getLatestInstalledScriptsByUserId(0) } returns installedScripts

        val result = repository.getLatestInstalledScriptsByUserId(0)

        assertEquals(installedScripts, result)
        coVerify { installationDao.getLatestInstalledScriptsByUserId(0) }
    }

    // --- InstalledFile delegation ---

    @Test
    fun `insertInstalledFile delegates to dao`() = runTest {
        val file = InstalledFile(installationId = 1L, destPath = "/p", wasOverwrite = false)
        coEvery { installedFileDao.insert(file) } returns 1L

        val result = repository.insertInstalledFile(file)
        assertEquals(1L, result)
    }

    @Test
    fun `insertInstalledFiles delegates to dao`() = runTest {
        val files = listOf(
            InstalledFile(installationId = 1L, destPath = "/p1", wasOverwrite = false),
            InstalledFile(installationId = 1L, destPath = "/p2", wasOverwrite = true)
        )
        coEvery { installedFileDao.insertAll(files) } just Runs

        repository.insertInstalledFiles(files)
        coVerify { installedFileDao.insertAll(files) }
    }

    @Test
    fun `getInstalledFilesByInstallation delegates to dao`() = runTest {
        val files = listOf(
            InstalledFile(id = 1L, installationId = 10L, destPath = "/p", wasOverwrite = false)
        )
        coEvery { installedFileDao.getByInstallationId(10L) } returns files

        val result = repository.getInstalledFilesByInstallation(10L)
        assertEquals(files, result)
    }

    @Test
    fun `getActiveFileOwnershipConflicts delegates to dao and de duplicates by path`() = runTest {
        val conflicts = listOf(
            FileOwnershipConflict(
                installedFileId = 3L,
                installationId = 20L,
                scriptId = 2L,
                scriptName = "Old Script",
                destPath = "/path/a",
                installedAt = 20L
            ),
            FileOwnershipConflict(
                installedFileId = 4L,
                installationId = 19L,
                scriptId = 3L,
                scriptName = "Older Script",
                destPath = "/path/a",
                installedAt = 10L
            )
        )
        coEvery { installedFileDao.getActiveOwnershipConflicts(listOf("/path/a"), 0) } returns conflicts

        val result = repository.getActiveFileOwnershipConflicts(listOf("/path/a"), 0)

        assertEquals(listOf(conflicts.first()), result)
        coVerify { installedFileDao.getActiveOwnershipConflicts(listOf("/path/a"), 0) }
    }

    @Test
    fun `reactivateInstalledFilesSupersededBy returns affected installations`() = runTest {
        coEvery { installedFileDao.getInstallationIdsSupersededBy(10L) } returns listOf(1L, 2L, 1L)
        coEvery { installedFileDao.reactivateSupersededBy(10L) } just Runs

        val result = repository.reactivateInstalledFilesSupersededBy(10L)

        assertEquals(setOf(1L, 2L), result)
        coVerify { installedFileDao.reactivateSupersededBy(10L) }
    }

    @Test
    fun `getSupersedingInstallationIds delegates to dao`() = runTest {
        coEvery { installedFileDao.getSupersedingInstallationIds(10L) } returns listOf(20L, 30L, 20L)

        val result = repository.getSupersedingInstallationIds(10L)

        assertEquals(setOf(20L, 30L), result)
        coVerify { installedFileDao.getSupersedingInstallationIds(10L) }
    }

    @Test
    fun `deleteInstalledFilesByInstallation delegates to dao`() = runTest {
        coEvery { installedFileDao.deleteByInstallationId(10L) } just Runs

        repository.deleteInstalledFilesByInstallation(10L)
        coVerify { installedFileDao.deleteByInstallationId(10L) }
    }
}
