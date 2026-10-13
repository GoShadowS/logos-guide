package com.logos.navigator.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.logos.navigator.data.database.dao.CategoriesDao
import com.logos.navigator.data.database.dao.FavoritesDao
import com.logos.navigator.data.database.dao.HistoryDao
import com.logos.navigator.data.database.dao.SavedRoutesDao
import com.logos.navigator.data.database.entity.FavoriteCategoryEntity
import com.logos.navigator.data.database.entity.FavoriteRoomEntity
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import com.logos.navigator.data.database.entity.SavedRouteEntity

@Database(
    entities = [
        FavoriteRoomEntity::class,
        FavoriteCategoryEntity::class,
        RouteHistoryEntity::class,
        SavedRouteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun categoriesDao(): CategoriesDao
    abstract fun historyDao(): HistoryDao
    abstract fun savedRoutesDao(): SavedRoutesDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Переход с первой схемы избранного к схеме с папками и историей маршрутов. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favorite_rooms ADD COLUMN customName TEXT")
                database.execSQL("ALTER TABLE favorite_rooms ADD COLUMN categoryId INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_rooms_categoryId ON favorite_rooms(categoryId)")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, iconName TEXT NOT NULL, colorArgb INTEGER NOT NULL, sortOrder INTEGER NOT NULL)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_categories_sortOrder ON favorite_categories(sortOrder)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS route_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromRoomId TEXT, toRoomId TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, pathLengthMeters REAL NOT NULL, " +
                        "fromLabel TEXT, fromFloor INTEGER)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_route_history_timestamp ON route_history(timestamp)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS saved_routes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, fromRoomId TEXT, " +
                        "toRoomId TEXT NOT NULL, categoryId INTEGER, createdAt INTEGER NOT NULL, " +
                        "fromFloor INTEGER, pathLengthMeters REAL)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_routes_createdAt ON saved_routes(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_routes_categoryId ON saved_routes(categoryId)")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "logos_favorites.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
