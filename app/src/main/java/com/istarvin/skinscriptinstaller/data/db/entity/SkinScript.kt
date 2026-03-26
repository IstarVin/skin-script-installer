package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skin_scripts")
data class SkinScript(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val importedAt: Long = System.currentTimeMillis(),
    val storagePath: String // filesDir/scripts/<id>/
)

