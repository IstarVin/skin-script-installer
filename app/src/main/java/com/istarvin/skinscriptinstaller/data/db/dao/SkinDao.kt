package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import kotlinx.coroutines.flow.Flow

@Dao
interface SkinDao {
    @Query("SELECT * FROM skins WHERE heroId = :heroId ORDER BY name ASC")
    fun getByHeroId(heroId: Long): Flow<List<Skin>>

    @Query("SELECT * FROM skins WHERE heroId = :heroId ORDER BY name ASC")
    suspend fun getByHeroIdOnce(heroId: Long): List<Skin>

    @Query("SELECT * FROM skins WHERE heroId = :heroId AND name = :name LIMIT 1")
    suspend fun getByHeroIdAndName(heroId: Long, name: String): Skin?

    @Query("SELECT * FROM skins WHERE id = :id")
    suspend fun getById(id: Long): Skin?

    @Query("SELECT * FROM skins ORDER BY id ASC")
    suspend fun getAllOnce(): List<Skin>

    @Insert
    suspend fun insert(skin: Skin): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(skins: List<Skin>)

    @Query("DELETE FROM skins")
    suspend fun clearAll()
}
