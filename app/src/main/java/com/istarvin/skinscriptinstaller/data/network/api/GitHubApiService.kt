package com.istarvin.skinscriptinstaller.data.network.api

import com.istarvin.skinscriptinstaller.data.network.dto.GitHubReleaseDto
import retrofit2.http.GET

interface GitHubApiService {
    @GET("repos/IstarVin/skin-script-installer/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseDto
}
