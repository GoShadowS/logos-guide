package com.logos.navigator.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.logos.navigator.R
import com.logos.navigator.data.database.AppDatabase
import com.logos.navigator.data.database.entity.FavoriteCategoryEntity
import com.logos.navigator.data.database.entity.FavoriteRoomEntity
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import com.logos.navigator.data.database.entity.SavedRouteEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

/** Репозиторий избранного: транзакции, сортировка, история, сохранённые маршруты и JSON-экспорт. */
class FavoritesRepository(context: Context, private val database: AppDatabase) {
    private val appContext = context.applicationContext
    private val favoritesDao = database.favoritesDao()
    private val categoriesDao = database.categoriesDao()
    private val historyDao = database.historyDao()
    private val savedRoutesDao = database.savedRoutesDao()
    private val gson = Gson()

    val favorites: Flow<List<FavoriteRoomEntity>> = favoritesDao.observeAll()
    val categories: Flow<List<FavoriteCategoryEntity>> = categoriesDao.observeAll()
    val savedRoutes: Flow<List<SavedRouteEntity>> = savedRoutesDao.observeAll()
    fun history(maximum: Int): Flow<List<RouteHistoryEntity>> = historyDao.observeRecent(maximum.coerceIn(1, 100))

    /** @return true если помещение добавлено, false если оно было удалено. */
    suspend fun toggleFavorite(roomId: String): Boolean = database.withTransaction {
        val current = favoritesDao.findByRoomId(roomId)
        if (current != null) {
            favoritesDao.deleteByRoomId(roomId)
            false
        } else {
            favoritesDao.insert(FavoriteRoomEntity(roomId = roomId, sortOrder = favoritesDao.nextSortOrder()))
            true
        }
    }

    suspend fun removeFavorite(roomId: String): FavoriteRoomEntity? = database.withTransaction {
        val current = favoritesDao.findByRoomId(roomId) ?: return@withTransaction null
        favoritesDao.deleteByRoomId(roomId)
        current
    }

    suspend fun restoreFavorite(entity: FavoriteRoomEntity) = database.withTransaction {
        favoritesDao.restore(entity)
    }

    suspend fun renameFavorite(roomId: String, name: String?) {
        favoritesDao.rename(roomId, name?.trim()?.takeIf(String::isNotEmpty))
    }

    suspend fun assignCategory(roomId: String, categoryId: Long?) {
        favoritesDao.assignCategory(roomId, categoryId)
    }

    /** Меняет порядок соседнего элемента и атомарно сохраняет новые sortOrder. */
    suspend fun moveFavorite(roomId: String, direction: Int) = database.withTransaction {
        if (direction == 0) return@withTransaction
        val rows = favoritesDao.getAll().toMutableList()
        val from = rows.indexOfFirst { it.roomId == roomId }
        if (from < 0 || rows.isEmpty()) return@withTransaction
        val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, rows.lastIndex)
        if (to == from) return@withTransaction
        val moving = rows.removeAt(from)
        rows.add(to, moving)
        rows.forEachIndexed { index, favorite -> favoritesDao.setOrder(favorite.roomId, index) }
    }

    suspend fun recordVisit(roomId: String) = favoritesDao.recordVisit(roomId, System.currentTimeMillis())

    suspend fun createCategory(name: String, iconName: String, colorArgb: Int): Long {
        val safeName = name.trim().take(40)
        require(safeName.isNotEmpty()) { appContext.getString(R.string.category_name_empty) }
        return categoriesDao.insert(
            FavoriteCategoryEntity(
                name = safeName,
                iconName = iconName,
                colorArgb = colorArgb,
                sortOrder = categoriesDao.nextSortOrder(),
            ),
        )
    }

    suspend fun renameCategory(id: Long, name: String) {
        val current = categoriesDao.findById(id) ?: return
        val safeName = name.trim().take(40)
        require(safeName.isNotEmpty()) { appContext.getString(R.string.category_name_empty) }
        categoriesDao.update(current.copy(name = safeName))
    }

    suspend fun deleteCategory(id: Long) = database.withTransaction {
        favoritesDao.clearCategory(id)
        savedRoutesDao.clearCategory(id)
        categoriesDao.deleteById(id)
    }

    suspend fun moveCategory(id: Long, direction: Int) = database.withTransaction {
        val rows = categoriesDao.observeAllOnce().toMutableList()
        val from = rows.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, rows.lastIndex)
        if (from == to) return@withTransaction
        val moving = rows.removeAt(from)
        rows.add(to, moving)
        rows.forEachIndexed { index, category -> categoriesDao.update(category.copy(sortOrder = index)) }
    }

    suspend fun recordRoute(entry: RouteHistoryEntity, maximum: Int) = database.withTransaction {
        historyDao.insert(entry)
        historyDao.keepNewest(maximum.coerceIn(1, 100))
        entry.fromRoomId?.let { favoritesDao.recordVisit(it, entry.timestamp) }
        favoritesDao.recordVisit(entry.toRoomId, entry.timestamp)
    }

    suspend fun clearHistory() = historyDao.clear()

    suspend fun removeHistoryEntry(id: Long): RouteHistoryEntity? = database.withTransaction {
        val entry = historyDao.findById(id) ?: return@withTransaction null
        historyDao.deleteById(id)
        entry
    }

    suspend fun restoreHistoryEntry(entry: RouteHistoryEntity) = database.withTransaction {
        historyDao.restore(entry)
    }

    suspend fun saveRoute(route: SavedRouteEntity): Long {
        require(route.name.isNotBlank()) { appContext.getString(R.string.saved_route_name_required) }
        return savedRoutesDao.insert(route.copy(name = route.name.trim().take(60)))
    }

    suspend fun renameSavedRoute(id: Long, name: String) {
        val route = savedRoutesDao.findById(id) ?: return
        require(name.isNotBlank()) { appContext.getString(R.string.saved_route_name_empty) }
        savedRoutesDao.update(route.copy(name = name.trim().take(60)))
    }

    suspend fun removeSavedRoute(id: Long): SavedRouteEntity? = database.withTransaction {
        val route = savedRoutesDao.findById(id) ?: return@withTransaction null
        savedRoutesDao.deleteById(id)
        route
    }

    suspend fun restoreSavedRoute(route: SavedRouteEntity) = database.withTransaction {
        savedRoutesDao.restore(route)
    }

    suspend fun clearAllFavorites() = database.withTransaction {
        favoritesDao.getAll().forEach { favoritesDao.deleteById(it.id) }
    }

    suspend fun exportFavorites(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val payload = database.withTransaction {
                FavoritesExport(
                    schemaVersion = EXPORT_VERSION,
                    categories = categoriesDao.observeAllOnce(),
                    favorites = favoritesDao.getAll(),
                    history = historyDao.getAll(),
                    savedRoutes = savedRoutesDao.getAll(),
                )
            }
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IOException(appContext.getString(R.string.backup_write_error))
            output.bufferedWriter(Charsets.UTF_8).use { writer -> gson.toJson(payload, writer) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            val message = if (exception.message == appContext.getString(R.string.backup_write_error)) {
                appContext.getString(R.string.backup_write_error)
            } else {
                appContext.getString(R.string.backup_export_failed)
            }
            throw IOException(message, exception)
        }
    }

    suspend fun importFavorites(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException(appContext.getString(R.string.backup_read_error))
            val payload = input.bufferedReader(Charsets.UTF_8).use { reader ->
                try {
                    gson.fromJson(reader, FavoritesExport::class.java)
                        ?: throw IOException(appContext.getString(R.string.backup_invalid))
                } catch (exception: JsonParseException) {
                    throw IOException(appContext.getString(R.string.backup_invalid), exception)
                }
            }
            require(payload.schemaVersion in 1..EXPORT_VERSION) {
                appContext.getString(R.string.backup_version_unsupported)
            }
            database.withTransaction {
                // Импорт дополняет локальные данные: идентификаторы из файла не должны перезаписывать
                // существующие папки и избранное с теми же id, поэтому строки получают новые id.
                val categoryIds = HashMap<Long, Long>()
                val existingCategories = categoriesDao.observeAllOnce()
                var nextCategoryOrder = categoriesDao.nextSortOrder()
                payload.categories.orEmpty().forEach { category ->
                    val existing = existingCategories.firstOrNull { it.name.equals(category.name, ignoreCase = true) }
                    categoryIds[category.id] = existing?.id
                        ?: categoriesDao.insert(category.copy(id = 0, sortOrder = nextCategoryOrder++))
                }
                var nextFavoriteOrder = favoritesDao.nextSortOrder()
                payload.favorites.orEmpty().forEach { favorite ->
                    if (favoritesDao.findByRoomId(favorite.roomId) == null) {
                        favoritesDao.insert(
                            favorite.copy(
                                id = 0,
                                categoryId = favorite.categoryId?.let(categoryIds::get),
                                sortOrder = nextFavoriteOrder++,
                            ),
                        )
                    }
                }
                val knownHistory = historyDao.getAll().map { it.toRoomId to it.timestamp }.toSet()
                payload.history.orEmpty()
                    .filter { (it.toRoomId to it.timestamp) !in knownHistory }
                    .forEach { historyDao.insert(it.copy(id = 0)) }
                val knownRoutes = savedRoutesDao.getAll().map { Triple(it.fromRoomId, it.toRoomId, it.createdAt) }.toSet()
                payload.savedRoutes.orEmpty()
                    .filter { Triple(it.fromRoomId, it.toRoomId, it.createdAt) !in knownRoutes }
                    .forEach { route ->
                        savedRoutesDao.insert(route.copy(id = 0, categoryId = route.categoryId?.let(categoryIds::get)))
                    }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (exception: IOException) {
            val knownMessages = setOf(
                appContext.getString(R.string.backup_read_error),
                appContext.getString(R.string.backup_invalid),
            )
            if (exception.message in knownMessages) throw exception
            throw IOException(appContext.getString(R.string.backup_import_failed), exception)
        } catch (exception: Exception) {
            throw IOException(appContext.getString(R.string.backup_import_failed), exception)
        }
    }

    private companion object {
        const val EXPORT_VERSION = 2
    }
}

/** DTO экспорта; массивы nullable для корректной обработки старых/неполных резервных копий. */
private data class FavoritesExport(
    val schemaVersion: Int = 2,
    val categories: List<FavoriteCategoryEntity>? = emptyList(),
    val favorites: List<FavoriteRoomEntity>? = emptyList(),
    val history: List<RouteHistoryEntity>? = emptyList(),
    val savedRoutes: List<SavedRouteEntity>? = emptyList(),
)
