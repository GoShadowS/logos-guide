package com.logos.navigator.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Недавно построенный маршрут. fromRoomId может быть null, если стартовали с точки на карте. */
@Entity(tableName = "route_history", indices = [Index(value = ["timestamp"])])
data class RouteHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromRoomId: String? = null,
    val toRoomId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val pathLengthMeters: Double,
    val fromLabel: String? = null,
    val fromFloor: Int? = null,
)
