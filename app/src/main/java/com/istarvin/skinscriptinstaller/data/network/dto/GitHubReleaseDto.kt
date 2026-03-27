package com.istarvin.skinscriptinstaller.data.network.dto

import com.google.gson.annotations.SerializedName

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    val name: String = "",
    val body: String = ""
)
