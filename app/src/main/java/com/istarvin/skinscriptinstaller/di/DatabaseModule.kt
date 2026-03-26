package com.istarvin.skinscriptinstaller.di

import android.content.Context
import androidx.room.Room
import com.istarvin.skinscriptinstaller.data.db.AppDatabase
import com.istarvin.skinscriptinstaller.data.db.dao.HeroDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.migrations.DatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "skin_script_installer.db"
        )
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3
            )
            .build()
    }

    @Provides
    fun provideSkinScriptDao(db: AppDatabase): SkinScriptDao = db.skinScriptDao()

    @Provides
    fun provideInstallationDao(db: AppDatabase): InstallationDao = db.installationDao()

    @Provides
    fun provideInstalledFileDao(db: AppDatabase): InstalledFileDao = db.installedFileDao()

    @Provides
    fun provideHeroDao(db: AppDatabase): HeroDao = db.heroDao()

    @Provides
    fun provideSkinDao(db: AppDatabase): SkinDao = db.skinDao()
}

