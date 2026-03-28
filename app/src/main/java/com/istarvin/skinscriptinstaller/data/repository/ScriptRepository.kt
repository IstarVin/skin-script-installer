package com.istarvin.skinscriptinstaller.data.repository

import com.istarvin.skinscriptinstaller.data.db.dao.HeroDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.AppDatabase
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import com.istarvin.skinscriptinstaller.data.db.query.HeroInstallationConflict
import com.istarvin.skinscriptinstaller.data.db.query.LatestInstalledScript
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepository @Inject constructor(
    private val appDatabase: AppDatabase,
    private val skinScriptDao: SkinScriptDao,
    private val installationDao: InstallationDao,
    private val installedFileDao: InstalledFileDao,
    private val heroDao: HeroDao,
    private val skinDao: SkinDao
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

    fun observeLatestInstallation(scriptId: Long, userId: Int): Flow<Installation?> =
        installationDao.observeLatestByScriptIdAndUserId(scriptId, userId)

    suspend fun getInstallationById(id: Long): Installation? =
        installationDao.getById(id)

    suspend fun getActiveHeroInstallationConflicts(
        heroId: Long,
        userId: Int,
        excludeScriptId: Long
    ): List<HeroInstallationConflict> =
        installationDao.getActiveConflictsByHeroId(
            heroId = heroId,
            userId = userId,
            excludeScriptId = excludeScriptId
        )

    fun getLatestInstallations(): Flow<List<Installation>> =
        installationDao.getLatestInstallations()

    fun getLatestInstallations(userId: Int): Flow<List<Installation>> =
        installationDao.getLatestInstallationsByUserId(userId)

    suspend fun getLatestInstallationsOnce(userId: Int): List<Installation> =
        installationDao.getLatestInstallationsByUserIdOnce(userId)

    suspend fun getLatestInstalledScriptsByUserId(userId: Int): List<LatestInstalledScript> =
        installationDao.getLatestInstalledScriptsByUserId(userId)

    // --- InstalledFile ---

    suspend fun insertInstalledFile(file: InstalledFile): Long =
        installedFileDao.insert(file)

    suspend fun insertInstalledFiles(files: List<InstalledFile>) =
        installedFileDao.insertAll(files)

    suspend fun getInstalledFilesByInstallation(installationId: Long): List<InstalledFile> =
        installedFileDao.getByInstallationId(installationId)

    suspend fun deleteInstalledFilesByInstallation(installationId: Long) =
        installedFileDao.deleteByInstallationId(installationId)

    suspend fun getAllScriptsOnce(): List<SkinScript> = skinScriptDao.getAllOnce()

    suspend fun getAllInstallationsOnce(): List<Installation> = installationDao.getAllOnce()

    suspend fun getAllInstalledFilesOnce(): List<InstalledFile> = installedFileDao.getAllOnce()

    // --- Hero ---

    fun getAllHeroes(): Flow<List<Hero>> = heroDao.getAll()

    suspend fun getAllHeroesOnce(): List<Hero> = heroDao.getAllOnce()

    suspend fun getHeroByName(name: String): Hero? = heroDao.getByName(name)

    suspend fun getHeroById(id: Long): Hero? = heroDao.getById(id)

    suspend fun insertHero(hero: Hero): Long = heroDao.insert(hero)

    suspend fun getHeroCount(): Int = heroDao.count()

    suspend fun syncHeroCatalog(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        appDatabase.withTransaction {
            val heroes = items.map { (name, _) -> Hero(name = name) }
            heroDao.insertAllIgnore(heroes)
            items.forEach { (name, iconUrl) ->
                heroDao.updateHeroIconByName(name, iconUrl)
            }
        }
    }

    // --- Skin ---

    fun getSkinsByHeroId(heroId: Long): Flow<List<Skin>> = skinDao.getByHeroId(heroId)

    suspend fun getSkinsByHeroIdOnce(heroId: Long): List<Skin> = skinDao.getByHeroIdOnce(heroId)

    suspend fun getSkinByHeroIdAndName(heroId: Long, name: String): Skin? =
        skinDao.getByHeroIdAndName(heroId, name)

    suspend fun getSkinById(id: Long): Skin? = skinDao.getById(id)

    suspend fun insertSkin(skin: Skin): Long = skinDao.insert(skin)

    suspend fun getAllSkinsOnce(): List<Skin> = skinDao.getAllOnce()

    suspend fun deleteHero(id: Long) = heroDao.deleteById(id)

    suspend fun updateHeroName(id: Long, name: String) = heroDao.updateName(id, name)

    suspend fun deleteSkin(id: Long) = skinDao.deleteById(id)

    suspend fun updateSkinName(id: Long, name: String) = skinDao.updateName(id, name)

    suspend fun countScriptsByHeroId(heroId: Long): Int = heroDao.countScriptsByHeroId(heroId)

    suspend fun countScriptsBySkinId(skinId: Long): Int = skinDao.countScriptsBySkinId(skinId)

    // --- Script Classification ---

    suspend fun updateScript(script: SkinScript) = skinScriptDao.update(script)

    // --- Backup ---

    suspend fun replaceAllBackupData(
        scripts: List<SkinScript>,
        installations: List<Installation>,
        installedFiles: List<InstalledFile>,
        heroes: List<Hero> = emptyList(),
        skins: List<Skin> = emptyList()
    ) {
        appDatabase.withTransaction {
            installedFileDao.clearAll()
            installationDao.clearAll()
            skinScriptDao.clearAll()
            skinDao.clearAll()
            heroDao.clearAll()

            if (heroes.isNotEmpty()) {
                heroDao.insertAllReplace(heroes)
            }
            if (skins.isNotEmpty()) {
                skinDao.insertAllReplace(skins)
            }
            if (scripts.isNotEmpty()) {
                skinScriptDao.insertAllReplace(scripts)
            }
            if (installations.isNotEmpty()) {
                installationDao.insertAllReplace(installations)
            }
            if (installedFiles.isNotEmpty()) {
                installedFileDao.insertAllReplace(installedFiles)
            }
        }
    }
}
