package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile

@Dao
interface InstalledFileDao {
    @Insert
    suspend fun insertAll(files: List<InstalledFile>)

    @Insert
    suspend fun insert(file: InstalledFile): Long

    @Query("SELECT * FROM installed_files WHERE installationId = :installationId")
    suspend fun getByInstallationId(installationId: Long): List<InstalledFile>

    @Query("DELETE FROM installed_files WHERE installationId = :installationId")
    suspend fun deleteByInstallationId(installationId: Long)
}

