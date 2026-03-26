package com.istarvin.skinscriptinstaller.service

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import javax.inject.Inject

class Zip4jArchiveService @Inject constructor() : ArchiveService {

    companion object {
        private const val MAX_ENTRY_COUNT = 20_000
        private const val MAX_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L // 1 GB
    }

    override fun inspectZip(zipFile: File): Result<ArchiveInspection> {
        return runCatching {
            val zip = ZipFile(zipFile)
            ArchiveInspection(encrypted = zip.isEncrypted)
        }.recoverCatching {
            throw CorruptedArchiveException(it)
        }
    }

    override fun extractZip(
        zipFile: File,
        destinationDir: File,
        password: CharArray?
    ): Result<Unit> {
        return try {
            val zip = ZipFile(zipFile)

            if (zip.isEncrypted && password == null) {
                return Result.failure(PasswordRequiredException())
            }

            if (zip.isEncrypted) {
                zip.setPassword(password)
            }

            validateArchiveEntries(zip, destinationDir)
            destinationDir.mkdirs()
            zip.extractAll(destinationDir.absolutePath)
            Result.success(Unit)
        } catch (e: ZipException) {
            Result.failure(mapZipException(e))
        } catch (e: Exception) {
            Result.failure(CorruptedArchiveException(e))
        } finally {
            password?.fill('\u0000')
        }
    }

    private fun validateArchiveEntries(zip: ZipFile, destinationDir: File) {
        val fileHeaders = zip.fileHeaders
        if (fileHeaders.size > MAX_ENTRY_COUNT) {
            throw ArchiveLimitExceededException("entry count")
        }

        var totalUncompressedBytes = 0L
        val destCanonicalPath = destinationDir.canonicalFile.toPath()

        for (header in fileHeaders) {
            val name = header.fileName ?: continue
            val targetPath = File(destinationDir, name).canonicalFile.toPath()
            if (!targetPath.startsWith(destCanonicalPath)) {
                throw UnsafeArchiveEntryException(name)
            }

            val size = header.uncompressedSize
            if (size > 0) {
                totalUncompressedBytes += size
                if (totalUncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw ArchiveLimitExceededException("uncompressed size")
                }
            }
        }
    }

    private fun mapZipException(e: ZipException): Exception {
        val message = e.message?.lowercase().orEmpty()
        return when {
            "wrong password" in message || "password" in message && "invalid" in message -> {
                InvalidPasswordException()
            }

            "encrypted" in message && "password" in message -> {
                PasswordRequiredException()
            }

            else -> CorruptedArchiveException(e)
        }
    }
}
