package com.istarvin.skinscriptinstaller.data.repository

import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
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
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        skinScriptDao = mockk(relaxed = true)
        installationDao = mockk(relaxed = true)
        installedFileDao = mockk(relaxed = true)
        repository = ScriptRepository(skinScriptDao, installationDao, installedFileDao)
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
    fun `deleteInstalledFilesByInstallation delegates to dao`() = runTest {
        coEvery { installedFileDao.deleteByInstallationId(10L) } just Runs

        repository.deleteInstalledFilesByInstallation(10L)
        coVerify { installedFileDao.deleteByInstallationId(10L) }
    }
}
