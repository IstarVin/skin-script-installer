package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import kotlinx.coroutines.flow.Flow

@Dao
interface HeroDao {
    @Query("SELECT * FROM heroes ORDER BY name ASC")
    fun getAll(): Flow<List<Hero>>

    @Query("SELECT * FROM heroes ORDER BY name ASC")
    suspend fun getAllOnce(): List<Hero>

    @Query("SELECT * FROM heroes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Hero?

    @Query("SELECT * FROM heroes WHERE id = :id")
    suspend fun getById(id: Long): Hero?

    @Insert
    suspend fun insert(hero: Hero): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(heroes: List<Hero>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(heroes: List<Hero>)

    @Query("SELECT COUNT(*) FROM heroes")
    suspend fun count(): Int

    @Query("UPDATE heroes SET heroIcon = :heroIcon WHERE name = :name")
    suspend fun updateHeroIconByName(name: String, heroIcon: String)

    @Query("DELETE FROM heroes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE heroes SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM skin_scripts WHERE heroId = :heroId")
    suspend fun countScriptsByHeroId(heroId: Long): Int

    @Query("DELETE FROM heroes")
    suspend fun clearAll()
}
