package com.istarvin.skinscriptinstaller.domain

import com.istarvin.skinscriptinstaller.data.catalog.HeroCatalogFallbackAssetDataSource
import com.istarvin.skinscriptinstaller.data.network.api.HeroApiService
import com.istarvin.skinscriptinstaller.data.repository.ScriptRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetchHeroCatalogUseCase @Inject constructor(
    private val heroApiService: HeroApiService,
    private val repository: ScriptRepository,
    private val heroCatalogFallbackAssetDataSource: HeroCatalogFallbackAssetDataSource
) {
    suspend fun execute(): Result<Int> = runCatching {
        val items = fetchRemoteItemsOrFallback()
        repository.syncHeroCatalog(items)
        items.size
    }

    private suspend fun fetchRemoteItemsOrFallback(): List<Pair<String, String>> {
        return try {
            val response = heroApiService.getHeroes(size = 1000)
            response.data?.records?.mapNotNull { record ->
                val heroData = record.data?.hero?.data ?: return@mapNotNull null
                if (heroData.name.isNotBlank()) heroData.name to heroData.head else null
            }.orEmpty()
        } catch (error: Exception) {
            if (repository.getHeroCount() != 0) {
                throw error
            }
            val fallbackItems = heroCatalogFallbackAssetDataSource.loadItems()
            if (fallbackItems.isEmpty()) {
                throw error
            }
            fallbackItems
        }
    }
}
