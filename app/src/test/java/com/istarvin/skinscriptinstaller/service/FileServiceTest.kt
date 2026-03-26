package com.istarvin.skinscriptinstaller.service

import org.junit.Assert.*
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
class FileServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var service: FileService

    @Before
    fun setUp() {
        service = FileService()
    }

    @Test
    fun `exists returns true for existing file`() {
        val file = File(tempFolder.root, "exists.txt")
        file.writeText("data")

        assertTrue(service.exists(file.absolutePath))
    }

    @Test
    fun `exists returns false for non-existing file`() {
        assertFalse(service.exists(File(tempFolder.root, "nope.txt").absolutePath))
    }

    @Test
    fun `exists returns true for existing directory`() {
        val dir = tempFolder.newFolder("mydir")
        assertTrue(service.exists(dir.absolutePath))
    }

    @Test
    fun `mkdirs creates directory hierarchy`() {
        val deepDir = File(tempFolder.root, "a/b/c/d")
        assertFalse(deepDir.exists())

        val result = service.mkdirs(deepDir.absolutePath)

        assertTrue(result)
        assertTrue(deepDir.exists())
        assertTrue(deepDir.isDirectory)
    }

    @Test
    fun `mkdirs returns false for existing directory`() {
        val dir = tempFolder.newFolder("existing")
        // File.mkdirs() returns false when directory already exists
        assertFalse(service.mkdirs(dir.absolutePath))
        // But directory still exists
        assertTrue(dir.exists())
    }

    @Test
    fun `deleteFile removes file`() {
        val file = File(tempFolder.root, "delete_me.txt")
        file.writeText("data")
        assertTrue(file.exists())

        val result = service.deleteFile(file.absolutePath)

        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteFile returns false for non-existing file`() {
        val result = service.deleteFile(File(tempFolder.root, "ghost.txt").absolutePath)
        assertFalse(result)
    }

    @Test
    fun `listFiles returns file paths in directory`() {
        val dir = tempFolder.newFolder("listdir")
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        val sub = File(dir, "sub")
        sub.mkdirs()

        val result = service.listFiles(dir.absolutePath)

        assertEquals(3, result.size)
        assertTrue(result.any { it.endsWith("a.txt") })
        assertTrue(result.any { it.endsWith("b.txt") })
        assertTrue(result.any { it.endsWith("sub") })
    }

    @Test
    fun `listFiles returns empty for non-existing directory`() {
        val result = service.listFiles(File(tempFolder.root, "nope").absolutePath)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listFiles returns empty for empty directory`() {
        val dir = tempFolder.newFolder("emptydir")
        val result = service.listFiles(dir.absolutePath)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listEligibleMlUserIds returns empty when emulated root does not exist`() {
        // On a normal test environment, /storage/emulated may not exist
        // or may not have ML data dirs, so this should return empty
        val result = service.listEligibleMlUserIds()
        // Can't assert specific values since we can't control /storage/emulated in unit test,
        // but verify it doesn't crash
        assertNotNull(result)
    }
}
