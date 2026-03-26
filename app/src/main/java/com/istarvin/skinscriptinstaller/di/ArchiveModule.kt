package com.istarvin.skinscriptinstaller.di

import com.istarvin.skinscriptinstaller.service.ArchiveService
import com.istarvin.skinscriptinstaller.service.Zip4jArchiveService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArchiveModule {

    @Provides
    @Singleton
    fun provideArchiveService(): ArchiveService = Zip4jArchiveService()
}
