package com.logos.navigator.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.logos.navigator.data.database.entity.FavoriteRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_rooms ORDER BY sortOrder ASC, addedAt DESC")
    fun observeAll(): Flow<List<FavoriteRoomEntity>>

    @Query("SELECT * FROM favorite_rooms WHERE categoryId = :categoryId ORDER BY sortOrder ASC, addedAt DESC")
    fun observeByCategory(categoryId: Long): Flow<List<FavoriteRoomEntity>>

    @Query("SELECT * FROM favorite_rooms ORDER BY sortOrder ASC, addedAt DESC")
    suspend fun getAll(): List<FavoriteRoomEntity>

    @Query("SELECT * FROM favorite_rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun findByRoomId(roomId: String): FavoriteRoomEntity?

    @Query("SELECT * FROM favorite_rooms WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FavoriteRoomEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM favorite_rooms")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteRoomEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(entity: FavoriteRoomEntity): Long

    @Update
    suspend fun update(entity: FavoriteRoomEntity)

    @Query("DELETE FROM favorite_rooms WHERE roomId = :roomId")
    suspend fun deleteByRoomId(roomId: String)

    @Query("DELETE FROM favorite_rooms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE favorite_rooms SET customName = :name WHERE roomId = :roomId")
    suspend fun rename(roomId: String, name: String?)

    @Query("UPDATE favorite_rooms SET categoryId = :categoryId WHERE roomId = :roomId")
    suspend fun assignCategory(roomId: String, categoryId: Long?)

    @Query("UPDATE favorite_rooms SET sortOrder = :order WHERE roomId = :roomId")
    suspend fun setOrder(roomId: String, order: Int)

    @Query("UPDATE favorite_rooms SET visitCount = visitCount + 1, lastVisitedAt = :visitedAt WHERE roomId = :roomId")
    suspend fun recordVisit(roomId: String, visitedAt: Long)

    @Query("UPDATE favorite_rooms SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)
}
