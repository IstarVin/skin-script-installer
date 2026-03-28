package com.istarvin.skinscriptinstaller.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.istarvin.skinscriptinstaller.data.db.entity.InstalledFile
import com.istarvin.skinscriptinstaller.data.db.query.FileOwnershipConflict

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

    @Query(
        "SELECT * FROM installed_files WHERE installationId = :installationId AND supersededByInstallationId IS NULL"
    )
    suspend fun getActiveByInstallationId(installationId: Long): List<InstalledFile>

    @Query(
        "SELECT COUNT(*) FROM installed_files WHERE installationId = :installationId AND supersededByInstallationId IS NULL"
    )
    suspend fun countActiveByInstallationId(installationId: Long): Int

    @Query(
        """
        SELECT installed_files.id AS installedFileId,
            installed_files.installationId AS installationId,
            installations.scriptId AS scriptId,
            skin_scripts.name AS scriptName,
            installed_files.destPath AS destPath,
            installations.installedAt AS installedAt
        FROM installed_files
        JOIN installations ON installations.id = installed_files.installationId
        JOIN skin_scripts ON skin_scripts.id = installations.scriptId
        WHERE installed_files.destPath IN (:destPaths)
            AND installations.userId = :userId
            AND installations.status IN ('installed', 'replaced')
            AND installed_files.supersededByInstallationId IS NULL
        ORDER BY installations.installedAt DESC, installations.id DESC
        """
    )
    suspend fun getActiveOwnershipConflicts(
        destPaths: List<String>,
        userId: Int
    ): List<FileOwnershipConflict>

    @Query(
        "UPDATE installed_files SET supersededByInstallationId = :supersededByInstallationId WHERE id IN (:fileIds)"
    )
    suspend fun markSuperseded(
        fileIds: List<Long>,
        supersededByInstallationId: Long
    )

    @Query(
        "SELECT DISTINCT installationId FROM installed_files WHERE supersededByInstallationId = :installationId"
    )
    suspend fun getInstallationIdsSupersededBy(installationId: Long): List<Long>

    @Query(
        "SELECT DISTINCT supersededByInstallationId FROM installed_files WHERE installationId = :installationId AND supersededByInstallationId IS NOT NULL"
    )
    suspend fun getSupersedingInstallationIds(installationId: Long): List<Long>

    @Query(
        "UPDATE installed_files SET supersededByInstallationId = NULL WHERE supersededByInstallationId = :installationId"
    )
    suspend fun reactivateSupersededBy(installationId: Long)

    @Query("DELETE FROM installed_files WHERE installationId = :installationId")
    suspend fun deleteByInstallationId(installationId: Long)

    @Query("SELECT * FROM installed_files ORDER BY id ASC")
    suspend fun getAllOnce(): List<InstalledFile>

    @Query("DELETE FROM installed_files")
    suspend fun clearAll()
}

