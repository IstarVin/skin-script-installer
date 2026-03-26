package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.istarvin.skinscriptinstaller.data.db.entity.Installation
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallationDao {
    @Insert
    suspend fun insert(installation: Installation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(installations: List<Installation>)

    @Update
    suspend fun update(installation: Installation)

    @Query("SELECT * FROM installations WHERE scriptId = :scriptId ORDER BY installedAt DESC LIMIT 1")
    suspend fun getLatestByScriptId(scriptId: Long): Installation?

    @Query(
        "SELECT * FROM installations WHERE scriptId = :scriptId AND userId = :userId ORDER BY installedAt DESC LIMIT 1"
    )
    suspend fun getLatestByScriptIdAndUserId(scriptId: Long, userId: Int): Installation?

    @Query("SELECT * FROM installations WHERE id = :id")
    suspend fun getById(id: Long): Installation?

    @Query("SELECT * FROM installations WHERE scriptId = :scriptId ORDER BY installedAt DESC")
    suspend fun getAllByScriptId(scriptId: Long): List<Installation>

    @Query("SELECT * FROM installations WHERE id IN (SELECT MAX(id) FROM installations GROUP BY scriptId)")
    fun getLatestInstallations(): Flow<List<Installation>>

    @Query(
        "SELECT * FROM installations WHERE id IN (" +
            "SELECT MAX(id) FROM installations WHERE userId = :userId GROUP BY scriptId" +
        ")"
    )
    fun getLatestInstallationsByUserId(userId: Int): Flow<List<Installation>>

    @Query("SELECT * FROM installations ORDER BY id ASC")
    suspend fun getAllOnce(): List<Installation>

    @Query("DELETE FROM installations")
    suspend fun clearAll()
}

