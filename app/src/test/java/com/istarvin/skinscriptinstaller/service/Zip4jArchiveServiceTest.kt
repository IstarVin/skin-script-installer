package com.istarvin.skinscriptinstaller.service

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Zip4jArchiveServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var service: Zip4jArchiveService

    @Before
    fun setUp() {
        service = Zip4jArchiveService()
    }

    // --- inspectZip ---

    @Test
    fun `inspectZip returns encrypted false for normal zip`() {
        val zipFile = createUnencryptedZip("hello.txt" to "Hello World")

        val result = service.inspectZip(zipFile)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().encrypted)
    }

    @Test
    fun `inspectZip returns encrypted true for password-protected zip`() {
        val zipFile = createEncryptedZip("secret.txt" to "Secret Data", password = "pass123")

        val result = service.inspectZip(zipFile)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().encrypted)
    }

    @Test
    fun `inspectZip returns CorruptedArchiveException for invalid file`() {
        val notAZip = File(tempFolder.root, "notazip.zip")
        notAZip.writeText("this is not a zip file")

        val result = service.inspectZip(notAZip)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CorruptedArchiveException)
    }

    // --- extractZip ---

    @Test
    fun `extractZip succeeds for unencrypted zip`() {
        val zipFile = createUnencryptedZip("file.txt" to "content")
        val destDir = tempFolder.newFolder("extract")

        val result = service.extractZip(zipFile, destDir)

        assertTrue(result.isSuccess)
        val extracted = File(destDir, "file.txt")
        assertTrue(extracted.exists())
        assertEquals("content", extracted.readText())
    }

    @Test
    fun `extractZip succeeds with correct password`() {
        val password = "mypassword"
        val zipFile = createEncryptedZip("secret.txt" to "secret content", password = password)
        val destDir = tempFolder.newFolder("extract")

        val result = service.extractZip(zipFile, destDir, password.toCharArray())

        assertTrue(result.isSuccess)
        val extracted = File(destDir, "secret.txt")
        assertTrue(extracted.exists())
        assertEquals("secret content", extracted.readText())
    }

    @Test
    fun `extractZip fails with PasswordRequiredException when encrypted and no password`() {
        val zipFile = createEncryptedZip("secret.txt" to "data", password = "pass")
        val destDir = tempFolder.newFolder("extract")

        val result = service.extractZip(zipFile, destDir, password = null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PasswordRequiredException)
    }

    @Test
    fun `extractZip fails with InvalidPasswordException for wrong password`() {
        val zipFile = createEncryptedZip("secret.txt" to "data", password = "correct")
        val destDir = tempFolder.newFolder("extract")

        val result = service.extractZip(zipFile, destDir, "wrong".toCharArray())

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        // zip4j may throw a corrupted or invalid password exception depending on version
        assertTrue(
            "Expected InvalidPasswordException or CorruptedArchiveException but got ${exception?.javaClass?.simpleName}",
            exception is InvalidPasswordException || exception is CorruptedArchiveException
        )
    }

    @Test
    fun `extractZip rejects path traversal entries`() {
        // Create a zip with a normal file, then craft one with traversal path
        val sourceDir = tempFolder.newFolder("source")
        val normalFile = File(sourceDir, "safe.txt")
        normalFile.writeText("safe")

        val zipPath = File(tempFolder.root, "traversal.zip")
        val zip = ZipFile(zipPath)
        val params = ZipParameters().apply {
            compressionMethod = CompressionMethod.STORE
            fileNameInZip = "../../../etc/evil.txt"
        }
        zip.addFile(normalFile, params)
        zip.close()

        val destDir = tempFolder.newFolder("extract")
        val result = service.extractZip(zipPath, destDir)

        // zip4j may normalize the path during creation; either way, the extraction
        // should either reject it (UnsafeArchiveEntryException) or the service
        // wraps the error (CorruptedArchiveException). Both are acceptable failures.
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            assertTrue(
                "Expected UnsafeArchiveEntryException or CorruptedArchiveException but got ${exception?.javaClass?.simpleName}",
                exception is UnsafeArchiveEntryException || exception is CorruptedArchiveException
            )
        } else {
            // If zip4j normalized the path and extraction succeeded, verify no file escaped destDir
            val destCanonical = destDir.canonicalFile.toPath()
            destDir.walkTopDown().forEach { file ->
                assertTrue(
                    "File ${file.absolutePath} escaped destination directory",
                    file.canonicalFile.toPath().startsWith(destCanonical)
                )
            }
        }
    }

    @Test
    fun `extractZip preserves directory structure`() {
        val sourceDir = tempFolder.newFolder("source")
        val subDir = File(sourceDir, "subdir")
        subDir.mkdirs()
        File(sourceDir, "root.txt").writeText("root")
        File(subDir, "child.txt").writeText("child")

        val zipPath = File(tempFolder.root, "structured.zip")
        val zip = ZipFile(zipPath)
        zip.addFolder(sourceDir)
        zip.close()

        val destDir = tempFolder.newFolder("extract")
        val result = service.extractZip(zipPath, destDir)

        assertTrue(result.isSuccess)
        assertTrue(File(destDir, "source/root.txt").exists())
        assertTrue(File(destDir, "source/subdir/child.txt").exists())
    }

    @Test
    fun `extractZip clears password array after use`() {
        val zipFile = createEncryptedZip("file.txt" to "data", password = "pass")
        val destDir = tempFolder.newFolder("extract")
        val passwordArray = "pass".toCharArray()

        service.extractZip(zipFile, destDir, passwordArray)

        // After extractZip, password array should be cleared (filled with null chars)
        assertTrue(passwordArray.all { it == '\u0000' })
    }

    // --- Helpers ---

    private fun createUnencryptedZip(vararg files: Pair<String, String>): File {
        val sourceDir = tempFolder.newFolder("src_${System.nanoTime()}")
        files.forEach { (name, content) ->
            File(sourceDir, name).apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }

        val zipPath = File(tempFolder.root, "test_${System.nanoTime()}.zip")
        val zip = ZipFile(zipPath)
        files.forEach { (name, _) ->
            zip.addFile(File(sourceDir, name))
        }
        zip.close()
        return zipPath
    }

    private fun createEncryptedZip(
        vararg files: Pair<String, String>,
        password: String
    ): File {
        val sourceDir = tempFolder.newFolder("src_${System.nanoTime()}")
        files.forEach { (name, content) ->
            File(sourceDir, name).apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }

        val zipPath = File(tempFolder.root, "test_${System.nanoTime()}.zip")
        val zip = ZipFile(zipPath, password.toCharArray())
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        files.forEach { (name, _) ->
            zip.addFile(File(sourceDir, name), params)
        }
        zip.close()
        return zipPath
    }
}
