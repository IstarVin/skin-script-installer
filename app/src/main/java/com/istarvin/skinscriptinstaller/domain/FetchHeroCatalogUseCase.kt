package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.network.api.HeroApiService
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchHeroCatalogUseCase @Inject constructor(
    private val heroApiService: HeroApiService,
    private val repository: ScriptRepository
) {
    suspend fun execute(): Result<Int> = runCatching {
        val response = heroApiService.getHeroes(size = 1000)
        val items = response.data?.records?.mapNotNull { record ->
            val heroData = record.data?.hero?.data ?: return@mapNotNull null
            if (heroData.name.isNotBlank()) heroData.name to heroData.head else null
        }.orEmpty()
        repository.syncHeroCatalog(items)
        items.size
    }
}
