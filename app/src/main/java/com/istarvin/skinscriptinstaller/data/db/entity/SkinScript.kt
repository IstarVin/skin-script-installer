package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skin_scripts",
    foreignKeys = [
        ForeignKey(
            entity = Hero::class,
            parentColumns = ["id"],
            childColumns = ["heroId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Skin::class,
            parentColumns = ["id"],
            childColumns = ["originalSkinId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Skin::class,
            parentColumns = ["id"],
            childColumns = ["replacementSkinId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["heroId"]),
        Index(value = ["originalSkinId"]),
        Index(value = ["replacementSkinId"])
    ]
)
data class SkinScript(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val importedAt: Long = System.currentTimeMillis(),
    val storagePath: String, // filesDir/scripts/<id>/
    val heroId: Long? = null,
    val originalSkinId: Long? = null,
    val replacementSkinId: Long? = null
)

