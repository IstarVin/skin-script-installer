package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.istarvin.skinscriptinstaller.data.db.entity.Installation

@Dao
interface InstallationDao {
    @Insert
    suspend fun insert(installation: Installation): Long

    @Update
    suspend fun update(installation: Installation)

    @Query("SELECT * FROM installations WHERE scriptId = :scriptId ORDER BY installedAt DESC LIMIT 1")
    suspend fun getLatestByScriptId(scriptId: Long): Installation?

    @Query("SELECT * FROM installations WHERE id = :id")
    suspend fun getById(id: Long): Installation?

    @Query("SELECT * FROM installations WHERE scriptId = :scriptId ORDER BY installedAt DESC")
    suspend fun getAllByScriptId(scriptId: Long): List<Installation>
}

