package com.istarvin.skinscriptinstaller.data.network.dto

import com.google.gson.annotations.SerializedName

data class HeroCatalogResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HeroCatalogData? = null
)

data class HeroCatalogData(
    val records: List<HeroCatalogRecord> = emptyList(),
    val total: Int = 0
)

data class HeroCatalogRecord(
    val data: HeroCatalogRecordData? = null
)

data class HeroCatalogRecordData(
    val hero: HeroCatalogHeroWrapper? = null,
    @SerializedName("hero_id") val heroId: Int = 0,
    val relation: HeroCatalogRelation? = null
)

data class HeroCatalogHeroWrapper(
    val data: HeroCatalogHeroData? = null
)

data class HeroCatalogHeroData(
    val head: String = "",
    val name: String = "",
    val smallmap: String = ""
)

data class HeroCatalogRelation(
    val assist: HeroCatalogRelationTarget? = null,
    val strong: HeroCatalogRelationTarget? = null,
    val weak: HeroCatalogRelationTarget? = null
)

data class HeroCatalogRelationTarget(
    @SerializedName("target_hero_id") val targetHeroIds: List<Int> = emptyList()
)

fun HeroCatalogResponse.toHeroCatalogItems(): List<Pair<String, String>> =
    data?.records?.mapNotNull { record ->
        val heroData = record.data?.hero?.data ?: return@mapNotNull null
        if (heroData.name.isNotBlank()) heroData.name to heroData.head else null
    }.orEmpty()
