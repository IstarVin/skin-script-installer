package com.istarvin.skinscriptinstaller.data.network.api

import com.istarvin.skinscriptinstaller.data.network.dto.HeroCatalogResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface HeroApiService {
    @GET("api/heroes")
    suspend fun getHeroes(@Query("size") size: Int = 1000): HeroCatalogResponse
}
