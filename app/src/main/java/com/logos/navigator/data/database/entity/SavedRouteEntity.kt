package com.logos.navigator.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сохранённая связка точек; путь повторно рассчитывается по актуальному локальному графу. */
@Entity(tableName = "saved_routes", indices = [Index(value = ["createdAt"]), Index(value = ["categoryId"])])
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromRoomId: String? = null,
    val toRoomId: String,
    val categoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val fromFloor: Int? = null,
    val pathLengthMeters: Double? = null,
)
