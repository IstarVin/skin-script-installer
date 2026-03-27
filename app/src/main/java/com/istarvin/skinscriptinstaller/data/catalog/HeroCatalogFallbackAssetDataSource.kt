package com.istarvin.skinscriptinstaller.data.catalog

import android.content.Context
import com.google.gson.Gson
import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val FALLBACK_ASSET_FILE_NAME = "heroes_fallback.json"

@Singleton
class HeroCatalogFallbackAssetDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    fun loadItems(): List<Pair<String, String>> {
        val json = context.assets.open(FALLBACK_ASSET_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }

        val response = gson.fromJson(json, HeroCatalogResponse::class.java)
        return response?.data?.records?.mapNotNull { record ->
            val heroData = record.data?.hero?.data ?: return@mapNotNull null
            if (heroData.name.isNotBlank()) heroData.name to heroData.head else null
        }.orEmpty()
    }
}
