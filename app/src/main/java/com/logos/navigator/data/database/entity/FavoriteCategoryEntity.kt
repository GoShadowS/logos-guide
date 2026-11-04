package com.logos.navigator.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_categories", indices = [Index(value = ["sortOrder"])])
data class FavoriteCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "folder",
    val colorArgb: Int = 0xFF2F5FE8.toInt(),
    val sortOrder: Int = 0,
)
