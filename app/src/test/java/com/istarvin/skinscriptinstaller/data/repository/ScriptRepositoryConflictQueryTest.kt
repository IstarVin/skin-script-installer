package com.istarvin.skinscriptinstaller.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.istarvin.skinscriptinstaller.data.db.AppDatabase
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ScriptRepositoryConflictQueryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ScriptRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScriptRepository(
            database,
            database.skinScriptDao(),
            database.installationDao(),
            database.installedFileDao(),
            database.heroDao(),
            database.skinDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `returns only active conflicts for same hero and user`() = runTest {
        val miyaId = repository.insertHero(Hero(name = "Miya"))
        val laylaId = repository.insertHero(Hero(name = "Layla"))

        val currentScriptId = insertScript(name = "Current Miya", heroId = miyaId)
        val activeConflictId = insertScript(name = "Miya Epic", heroId = miyaId)
        val restoredConflictId = insertScript(name = "Miya Old", heroId = miyaId)
        val otherUserConflictId = insertScript(name = "Miya Other User", heroId = miyaId)
        val otherHeroConflictId = insertScript(name = "Layla Basic", heroId = laylaId)

        insertInstallation(scriptId = currentScriptId, userId = 0, status = "installed", installedAt = 50)
        insertInstallation(scriptId = activeConflictId, userId = 0, status = "installed", installedAt = 200)
        insertInstallation(scriptId = restoredConflictId, userId = 0, status = "installed", installedAt = 100)
        insertInstallation(
            scriptId = restoredConflictId,
            userId = 0,
            status = "restored",
            installedAt = 150,
            restoredAt = 151
        )
        insertInstallation(scriptId = otherUserConflictId, userId = 10, status = "installed", installedAt = 300)
        insertInstallation(scriptId = otherHeroConflictId, userId = 0, status = "installed", installedAt = 400)

        val conflicts = repository.getActiveHeroInstallationConflicts(
            heroId = miyaId,
            userId = 0,
            excludeScriptId = currentScriptId
        )

        assertEquals(listOf("Miya Epic"), conflicts.map { it.scriptName })
        assertEquals(listOf(activeConflictId), conflicts.map { it.scriptId })
    }

    @Test
    fun `returns all active conflicts when legacy installs exist`() = runTest {
        val miyaId = repository.insertHero(Hero(name = "Miya"))
        val currentScriptId = insertScript(name = "Current Miya", heroId = miyaId)
        val firstConflictId = insertScript(name = "Miya Epic", heroId = miyaId)
        val secondConflictId = insertScript(name = "Miya Collector", heroId = miyaId)

        insertInstallation(scriptId = firstConflictId, userId = 0, status = "installed", installedAt = 100)
        insertInstallation(scriptId = secondConflictId, userId = 0, status = "installed", installedAt = 200)

        val conflicts = repository.getActiveHeroInstallationConflicts(
            heroId = miyaId,
            userId = 0,
            excludeScriptId = currentScriptId
        )

        assertEquals(
            listOf("Miya Collector", "Miya Epic"),
            conflicts.map { it.scriptName }
        )
        assertEquals(listOf(secondConflictId, firstConflictId), conflicts.map { it.scriptId })
    }

    @Test
    fun `getLatestInstalledScriptsByUserId returns only latest installed rows for requested user`() = runTest {
        val miyaId = repository.insertHero(Hero(name = "Miya"))
        val laylaId = repository.insertHero(Hero(name = "Layla"))

        val miyaActiveId = insertScript(name = "Miya Epic", heroId = miyaId)
        val miyaRestoredId = insertScript(name = "Miya Legacy", heroId = miyaId)
        val laylaOtherUserId = insertScript(name = "Layla Other User", heroId = laylaId)
        val laylaActiveId = insertScript(name = "Layla Basic", heroId = laylaId)

        insertInstallation(scriptId = miyaActiveId, userId = 0, status = "installed", installedAt = 100)
        insertInstallation(scriptId = miyaRestoredId, userId = 0, status = "installed", installedAt = 200)
        insertInstallation(
            scriptId = miyaRestoredId,
            userId = 0,
            status = "restored",
            installedAt = 250,
            restoredAt = 251
        )
        insertInstallation(scriptId = laylaOtherUserId, userId = 10, status = "installed", installedAt = 300)
        insertInstallation(scriptId = laylaActiveId, userId = 0, status = "installed", installedAt = 400)

        val installedScripts = repository.getLatestInstalledScriptsByUserId(0)

        assertEquals(
            listOf("Layla Basic", "Miya Epic"),
            installedScripts.map { it.scriptName }
        )
        assertEquals(
            listOf(laylaActiveId, miyaActiveId),
            installedScripts.map { it.scriptId }
        )
    }

    private suspend fun insertScript(name: String, heroId: Long): Long {
        return repository.insertScript(
            SkinScript(
                name = name,
                storagePath = "/tmp/$name",
                heroId = heroId
            )
        )
    }

    private suspend fun insertInstallation(
        scriptId: Long,
        userId: Int,
        status: String,
        installedAt: Long,
        restoredAt: Long? = null
    ): Long {
        return repository.insertInstallation(
            Installation(
                scriptId = scriptId,
                userId = userId,
                installedAt = installedAt,
                restoredAt = restoredAt,
                status = status
            )
        )
    }
}
