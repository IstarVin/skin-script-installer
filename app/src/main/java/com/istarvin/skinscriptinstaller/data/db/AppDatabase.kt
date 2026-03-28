package com.istarvin.skinscriptinstaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.istarvin.skinscriptinstaller.data.db.dao.HeroDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript

@Database(
    entities = [SkinScript::class, Installation::class, InstalledFile::class, Hero::class, Skin::class],
    version = AppDatabase.DATABASE_VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_VERSION = 5
    }

    abstract fun skinScriptDao(): SkinScriptDao
    abstract fun installationDao(): InstallationDao
    abstract fun installedFileDao(): InstalledFileDao
    abstract fun heroDao(): HeroDao
    abstract fun skinDao(): SkinDao
}

