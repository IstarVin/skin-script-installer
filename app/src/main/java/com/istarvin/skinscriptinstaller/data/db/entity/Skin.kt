package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skins",
    foreignKeys = [
        ForeignKey(
            entity = Hero::class,
            parentColumns = ["id"],
            childColumns = ["heroId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["heroId"]),
        Index(value = ["heroId", "name"], unique = true)
    ]
)
data class Skin(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val heroId: Long,
    val name: String
)
