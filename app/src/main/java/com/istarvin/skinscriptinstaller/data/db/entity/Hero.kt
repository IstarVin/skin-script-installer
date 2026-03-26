package com.istarvin.skinscriptinstaller.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heroes",
    indices = [Index(value = ["name"], unique = true)]
)
data class Hero(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val heroIcon: String? = null
)
