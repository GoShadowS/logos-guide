package com.logos.navigator.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Помещение из встроенного каталога, помеченное пользователем как избранное. */
@Entity(
    tableName = "favorite_rooms",
    indices = [Index(value = ["roomId"], unique = true), Index(value = ["categoryId"]), Index(value = ["sortOrder"])],
)
data class FavoriteRoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val customName: String? = null,
    val categoryId: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 0,
    val lastVisitedAt: Long? = null,
    val sortOrder: Int = 0,
)
