package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import javax.inject.Inject

class ClassifyScriptUseCase @Inject constructor(
    private val repository: ScriptRepository
) {
    /**
     * Classifies a script by hero, original skin, and replacement skin.
     * Finds or creates the Hero and Skin records as needed.
     * Every new hero automatically gets "Default" and "Basic" skins.
     */
    suspend fun execute(
        scriptId: Long,
        heroName: String,
        originalSkinName: String,
        replacementSkinName: String
    ): Result<Unit> = try {
        val script = repository.getScriptById(scriptId)
            ?: return Result.failure(Exception("Script not found"))

        val hero = repository.getHeroByName(heroName)
            ?: run {
                val id = repository.insertHero(Hero(name = heroName))
                val newHero = Hero(id = id, name = heroName)
                // Auto-create default skins for every new hero
                ensureDefaultSkins(id)
                newHero
            }

        val originalSkin = findOrCreateSkin(hero.id, originalSkinName)
        val replacementSkin = findOrCreateSkin(hero.id, replacementSkinName)

        repository.updateScript(
            script.copy(
                heroId = hero.id,
                originalSkinId = originalSkin.id,
                replacementSkinId = replacementSkin.id
            )
        )

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Removes classification from a script.
     */
    suspend fun clearClassification(scriptId: Long): Result<Unit> = try {
        val script = repository.getScriptById(scriptId)
            ?: return Result.failure(Exception("Script not found"))

        repository.updateScript(
            script.copy(heroId = null, originalSkinId = null, replacementSkinId = null)
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun findOrCreateSkin(heroId: Long, skinName: String): Skin {
        return repository.getSkinByHeroIdAndName(heroId, skinName)
            ?: run {
                val id = repository.insertSkin(Skin(heroId = heroId, name = skinName))
                Skin(id = id, heroId = heroId, name = skinName)
            }
    }

    private suspend fun ensureDefaultSkins(heroId: Long) {
        val defaults = listOf("Default", "Basic")
        for (name in defaults) {
            if (repository.getSkinByHeroIdAndName(heroId, name) == null) {
                repository.insertSkin(Skin(heroId = heroId, name = name))
            }
        }
    }
}
