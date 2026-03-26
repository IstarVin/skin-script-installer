package com.istarvin.skinscriptinstaller.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.istarvin.skinscriptinstaller.data.db.dao.InstalledFileDao
import com.istarvin.skinscriptinstaller.data.db.dao.InstallationDao
import com.istarvin.skinscriptinstaller.data.db.dao.SkinScriptDao
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript

@Database(
    entities = [SkinScript::class, Installation::class, InstalledFile::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun skinScriptDao(): SkinScriptDao
    abstract fun installationDao(): InstallationDao
    abstract fun installedFileDao(): InstalledFileDao
}

