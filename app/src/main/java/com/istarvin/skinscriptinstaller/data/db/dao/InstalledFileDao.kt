package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile

@Dao
interface InstalledFileDao {
    @Insert
    suspend fun insertAll(files: List<InstalledFile>)

    @Insert
    suspend fun insert(file: InstalledFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(files: List<InstalledFile>)

    @Query("SELECT * FROM installed_files WHERE installationId = :installationId")
    suspend fun getByInstallationId(installationId: Long): List<InstalledFile>

    @Query("DELETE FROM installed_files WHERE installationId = :installationId")
    suspend fun deleteByInstallationId(installationId: Long)

    @Query("SELECT * FROM installed_files ORDER BY id ASC")
    suspend fun getAllOnce(): List<InstalledFile>

    @Query("DELETE FROM installed_files")
    suspend fun clearAll()
}

