package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installations",
    foreignKeys = [
        ForeignKey(
            entity = SkinScript::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scriptId"), Index("userId")]
)
data class Installation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scriptId: Long,
    val userId: Int = 0,
    val installedAt: Long = System.currentTimeMillis(),
    val restoredAt: Long? = null,
    val status: String = InstallationStatus.INSTALLED // "installed" | "replaced" | "restored" | "superseded"
)

