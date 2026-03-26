package com.istarvin.skinscriptinstaller.service

import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
import java.io.File

/**
 * Shizuku UserService implementation. Runs in a separate process with shell (adb) permissions.
 * This is NOT an Android Service — it's a plain class instantiated by Shizuku.
 */
class FileService : IFileService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun openFileForRead(path: String): ParcelFileDescriptor {
        val file = File(path)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun writeFile(source: ParcelFileDescriptor, destPath: String): Boolean {
        return try {
            val dest = File(destPath)
            dest.parentFile?.mkdirs()
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun listFiles(path: String): List<String> {
        return try {
            File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun mkdirs(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

