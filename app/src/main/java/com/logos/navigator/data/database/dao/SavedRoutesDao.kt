package com.logos.navigator.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.logos.navigator.data.database.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRoutesDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedRouteEntity>

    @Query("SELECT * FROM saved_routes WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SavedRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: SavedRouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(route: SavedRouteEntity): Long

    @Update
    suspend fun update(route: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE saved_routes SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)
}
