package com.istarvin.skinscriptinstaller.data.network.dto

data class HeroCatalogResponse(
    val code: Int = 0,
    val data: HeroCatalogData? = null
)

data class HeroCatalogData(
    val records: List<HeroCatalogRecord> = emptyList()
)

data class HeroCatalogRecord(
    val data: HeroCatalogRecordData? = null
)

data class HeroCatalogRecordData(
    val hero: HeroCatalogHeroWrapper? = null
)

data class HeroCatalogHeroWrapper(
    val data: HeroCatalogHeroData? = null
)

data class HeroCatalogHeroData(
    val head: String = "",
    val name: String = ""
)
