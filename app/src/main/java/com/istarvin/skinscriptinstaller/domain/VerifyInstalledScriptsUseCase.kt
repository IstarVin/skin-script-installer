package com.istarvin.skinscriptinstaller.domain

import android.os.ParcelFileDescriptor
import com.istarvin.skinscriptinstaller.IFileService
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import com.istarvin.skinscriptinstaller.service.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyInstalledScriptsUseCase @Inject constructor(
    private val repository: ScriptRepository,
    private val shizukuManager: ShizukuManager
) {
    private val scanMutex = Mutex()

    suspend fun execute(): Boolean = withContext(Dispatchers.IO) {
        runScan { service ->
            val eligibleUserIds = service.listEligibleMlUserIds().toList().distinct().sorted()

            eligibleUserIds.forEach { userId ->
                currentCoroutineContext().ensureActive()
                verifyUserInstallations(userId, service)
            }

            true
        }
    }

    suspend fun execute(scriptId: Long, userId: Int): Boolean = withContext(Dispatchers.IO) {
        runScan { service ->
            val installation = repository.getLatestInstallation(scriptId, userId)
                ?: return@runScan false

            if (installation.status == InstallationStatus.RESTORED) {
                return@runScan false
            }

            val nextStatus = verifyInstallationStatus(installation, service)
            if (nextStatus != installation.status) {
                repository.updateInstallation(installation.copy(status = nextStatus))
            }

            true
        }
    }

    private suspend fun runScan(action: suspend (IFileService) -> Boolean): Boolean {
        if (!scanMutex.tryLock()) {
            return false
        }

        try {
            val service = shizukuManager.fileService.value ?: return false
            return action(service)
        } finally {
            scanMutex.unlock()
        }
    }

    private suspend fun verifyUserInstallations(userId: Int, service: IFileService) {
        val latestInstallations = repository.getLatestInstallationsOnce(userId)

        latestInstallations
            .asSequence()
            .filter { it.status != InstallationStatus.RESTORED }
            .forEach { installation ->
                currentCoroutineContext().ensureActive()
                val nextStatus = verifyInstallationStatus(installation, service)
                if (nextStatus != installation.status) {
                    repository.updateInstallation(installation.copy(status = nextStatus))
                }
            }
    }

    private suspend fun verifyInstallationStatus(
        installation: Installation,
        service: IFileService
    ): String {
        val script = repository.getScriptById(installation.scriptId)
            ?: return InstallationStatus.REPLACED

        val installedFiles = repository.getInstalledFilesByInstallation(installation.id)
        if (installedFiles.isEmpty()) {
            return InstallationStatus.REPLACED
        }

        val assetsDir = resolveImportedAssetsDir(script.storagePath)
        val hasMismatch = installedFiles.any { installedFile ->
            !matchesImportedScript(assetsDir, installation.userId, installedFile, service)
        }

        return if (hasMismatch) InstallationStatus.REPLACED else InstallationStatus.INSTALLED
    }

    private fun matchesImportedScript(
        assetsDir: File,
        userId: Int,
        installedFile: InstalledFile,
        service: IFileService
    ): Boolean {
        val relativePath = installedFileRelativePath(userId, installedFile.destPath) ?: return false
        val sourceFile = File(assetsDir, relativePath)

        if (!sourceFile.isFile || !service.exists(installedFile.destPath)) {
            return false
        }

        return try {
            sourceFile.inputStream().use { sourceInput ->
                ParcelFileDescriptor.AutoCloseInputStream(
                    service.openFileForRead(installedFile.destPath)
                ).use { targetInput ->
                    streamsMatch(sourceInput, targetInput)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun streamsMatch(source: InputStream, target: InputStream): Boolean {
        val sourceBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val targetBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val sourceRead = source.read(sourceBuffer)
            val targetRead = target.read(targetBuffer)

            if (sourceRead != targetRead) {
                return false
            }

            if (sourceRead == -1) {
                return true
            }

            for (index in 0 until sourceRead) {
                if (sourceBuffer[index] != targetBuffer[index]) {
                    return false
                }
            }
        }
    }
}