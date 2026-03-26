package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installed_files",
    foreignKeys = [
        ForeignKey(
            entity = Installation::class,
            parentColumns = ["id"],
            childColumns = ["installationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("installationId")]
)
data class InstalledFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val installationId: Long,
    val destPath: String, // absolute ML path on device
    val wasOverwrite: Boolean,
    val backupPath: String? = null // filesDir/backups/<installationId>/<relPath>
)

