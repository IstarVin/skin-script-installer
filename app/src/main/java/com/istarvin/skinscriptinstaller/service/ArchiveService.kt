package com.istarvin.skinscriptinstaller.service

import java.io.File

interface ArchiveService {
    fun inspectZip(zipFile: File): Result<ArchiveInspection>

    fun extractZip(
        zipFile: File,
        destinationDir: File,
        password: CharArray? = null
    ): Result<Unit>
}

data class ArchiveInspection(
    val encrypted: Boolean
)

sealed class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PasswordRequiredException : ArchiveException("This ZIP archive is password protected")

class InvalidPasswordException : ArchiveException("Incorrect ZIP password")

class CorruptedArchiveException(cause: Throwable? = null) :
    ArchiveException("Archive is invalid or corrupted", cause)

class UnsafeArchiveEntryException(entryName: String) :
    ArchiveException("Archive contains an unsafe entry: $entryName")

class ArchiveLimitExceededException(limitName: String) :
    ArchiveException("Archive exceeds allowed $limitName")
