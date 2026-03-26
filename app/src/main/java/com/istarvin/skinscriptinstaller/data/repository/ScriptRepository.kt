package com.istarvin.skinscriptinstaller.data.repository

import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepository @Inject constructor(
    private val skinScriptDao: SkinScriptDao,
    private val installationDao: InstallationDao,
    private val installedFileDao: InstalledFileDao
) {
    // --- SkinScript ---

    fun getAllScripts(): Flow<List<SkinScript>> = skinScriptDao.getAll()

    suspend fun getScriptById(id: Long): SkinScript? = skinScriptDao.getById(id)

    suspend fun insertScript(script: SkinScript): Long = skinScriptDao.insert(script)

    suspend fun deleteScript(script: SkinScript) = skinScriptDao.delete(script)

    suspend fun deleteScriptById(id: Long) = skinScriptDao.deleteById(id)

    // --- Installation ---

    suspend fun insertInstallation(installation: Installation): Long =
        installationDao.insert(installation)

    suspend fun updateInstallation(installation: Installation) =
        installationDao.update(installation)

    suspend fun getLatestInstallation(scriptId: Long): Installation? =
        installationDao.getLatestByScriptId(scriptId)

    suspend fun getLatestInstallation(scriptId: Long, userId: Int): Installation? =
        installationDao.getLatestByScriptIdAndUserId(scriptId, userId)

    suspend fun getInstallationById(id: Long): Installation? =
        installationDao.getById(id)

    fun getLatestInstallations(): Flow<List<Installation>> =
        installationDao.getLatestInstallations()

    fun getLatestInstallations(userId: Int): Flow<List<Installation>> =
        installationDao.getLatestInstallationsByUserId(userId)

    // --- InstalledFile ---

    suspend fun insertInstalledFile(file: InstalledFile): Long =
        installedFileDao.insert(file)

    suspend fun insertInstalledFiles(files: List<InstalledFile>) =
        installedFileDao.insertAll(files)

    suspend fun getInstalledFilesByInstallation(installationId: Long): List<InstalledFile> =
        installedFileDao.getByInstallationId(installationId)

    suspend fun deleteInstalledFilesByInstallation(installationId: Long) =
        installedFileDao.deleteByInstallationId(installationId)
}

