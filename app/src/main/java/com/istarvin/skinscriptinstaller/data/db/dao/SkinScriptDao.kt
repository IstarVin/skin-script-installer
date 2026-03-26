package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.istarvin.skinscriptinstaller.data.db.entity.SkinScript
import kotlinx.coroutines.flow.Flow

@Dao
interface SkinScriptDao {
    @Query("SELECT * FROM skin_scripts ORDER BY importedAt DESC")
    fun getAll(): Flow<List<SkinScript>>

    @Query("SELECT * FROM skin_scripts WHERE id = :id")
    suspend fun getById(id: Long): SkinScript?

    @Insert
    suspend fun insert(script: SkinScript): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(scripts: List<SkinScript>)

    @Update
    suspend fun update(script: SkinScript)

    @Delete
    suspend fun delete(script: SkinScript)

    @Query("DELETE FROM skin_scripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM skin_scripts ORDER BY id ASC")
    suspend fun getAllOnce(): List<SkinScript>

    @Query("DELETE FROM skin_scripts")
    suspend fun clearAll()
}

