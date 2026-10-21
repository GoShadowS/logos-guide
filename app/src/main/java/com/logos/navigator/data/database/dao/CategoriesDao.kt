package com.logos.navigator.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.logos.navigator.data.database.entity.FavoriteCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriesDao {
    @Query("SELECT * FROM favorite_categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<FavoriteCategoryEntity>>

    @Query("SELECT * FROM favorite_categories ORDER BY sortOrder ASC, id ASC")
    suspend fun observeAllOnce(): List<FavoriteCategoryEntity>

    @Query("SELECT * FROM favorite_categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FavoriteCategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM favorite_categories")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: FavoriteCategoryEntity): Long

    @Update
    suspend fun update(category: FavoriteCategoryEntity)

    @Query("DELETE FROM favorite_categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
