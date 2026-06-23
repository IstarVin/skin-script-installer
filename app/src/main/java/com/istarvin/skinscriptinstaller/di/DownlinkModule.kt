package com.istarvin.skinscriptinstaller.di

import com.istarvin.skinscriptinstaller.data.downlink.SFileDirectDownloadResolver
import com.istarvin.skinscriptinstaller.data.downlink.SFileDirectDownloadResolverImpl
import com.istarvin.skinscriptinstaller.data.network.http.HttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownlinkModule {

    @Provides
    @Singleton
    fun provideSFileDirectDownloadResolver(
        httpClient: HttpClient
    ): SFileDirectDownloadResolver = SFileDirectDownloadResolverImpl(httpClient)
}
