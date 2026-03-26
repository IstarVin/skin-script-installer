package com.istarvin.skinscriptinstaller.domain.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchiveUtils {
    fun zipDirectory(sourceDir: File, outputStream: FileOutputStream) {
        ZipOutputStream(outputStream).use { zipOut ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zipOut.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
        }
    }

    fun unzip(archive: File, destinationDir: File) {
        ZipInputStream(FileInputStream(archive)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val outFile = safeResolve(destinationDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        zipIn.copyTo(output)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    fun copyDirectory(sourceDir: File, destinationDir: File) {
        if (!sourceDir.exists()) return
        sourceDir.walkTopDown().forEach { source ->
            val target = File(destinationDir, source.relativeTo(sourceDir).invariantSeparatorsPath)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun safeResolve(baseDir: File, entryName: String): File {
        val target = File(baseDir, entryName)
        val baseCanonical = baseDir.canonicalFile
        val targetCanonical = target.canonicalFile
        if (!targetCanonical.path.startsWith(baseCanonical.path + File.separator)) {
            throw IllegalArgumentException("Invalid archive entry path")
        }
        return targetCanonical
    }
}
