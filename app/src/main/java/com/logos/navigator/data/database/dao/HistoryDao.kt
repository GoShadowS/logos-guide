package com.logos.navigator.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM route_history ORDER BY timestamp DESC LIMIT :maximum")
    fun observeRecent(maximum: Int): Flow<List<RouteHistoryEntity>>

    @Query("SELECT * FROM route_history ORDER BY timestamp DESC LIMIT 1000")
    suspend fun getAll(): List<RouteHistoryEntity>

    @Query("SELECT * FROM route_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): RouteHistoryEntity?

    @Insert
    suspend fun insert(entry: RouteHistoryEntity): Long

    @Insert
    suspend fun restore(entry: RouteHistoryEntity): Long

    @Query("DELETE FROM route_history")
    suspend fun clear()

    @Query("DELETE FROM route_history WHERE id NOT IN (SELECT id FROM route_history ORDER BY timestamp DESC LIMIT :maximum)")
    suspend fun keepNewest(maximum: Int)

    @Query("DELETE FROM route_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
