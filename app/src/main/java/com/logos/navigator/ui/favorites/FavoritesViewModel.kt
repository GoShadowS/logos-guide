package com.logos.navigator.ui.favorites

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.logos.navigator.R
import com.logos.navigator.data.database.AppDatabase
import com.logos.navigator.data.database.entity.FavoriteCategoryEntity
import com.logos.navigator.data.database.entity.FavoriteRoomEntity
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import com.logos.navigator.data.database.entity.SavedRouteEntity
import com.logos.navigator.data.datastore.SettingsDataStore
import com.logos.navigator.data.model.LoadedIndoorData
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.repository.FavoritesRepository
import com.logos.navigator.data.repository.IndoorRepository
import com.logos.navigator.utils.localizedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Снимок Room и локального каталога, используемый экраном «Избранное». */
data class FavoritesUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val rooms: List<Room> = emptyList(),
    val favoriteRooms: List<FavoriteRoomEntity> = emptyList(),
    val categories: List<FavoriteCategoryEntity> = emptyList(),
    val history: List<RouteHistoryEntity> = emptyList(),
    val savedRoutes: List<SavedRouteEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

/** Локальные избранное, папки, история и сохранённые маршруты. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(application)
    private val repository = FavoritesRepository(application, database)
    private val indoorRepository = IndoorRepository(application)
    private val settingsStore = SettingsDataStore(application)

    private val _state = MutableStateFlow(FavoritesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val loaded: LoadedIndoorData = indoorRepository.loadIndoorData()
                _state.update { it.copy(rooms = loaded.rooms, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = appContext.getString(
                            R.string.favorites_catalog_error,
                            exceptionMessage(exception),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.favorites.collectLatest { rows -> _state.update { it.copy(favoriteRooms = rows) } }
        }
        viewModelScope.launch {
            repository.categories.collectLatest { rows -> _state.update { it.copy(categories = rows) } }
        }
        viewModelScope.launch {
            repository.savedRoutes.collectLatest { rows -> _state.update { it.copy(savedRoutes = rows) } }
        }
        viewModelScope.launch {
            settingsStore.settingsFlow
                .flatMapLatest { preferences -> repository.history(preferences.maximumHistoryRecords) }
                .catch { exception ->
                    setError(appContext.getString(R.string.history_error, exceptionMessage(exception)))
                }
                .collectLatest { rows -> _state.update { it.copy(history = rows) } }
        }
    }

    fun toggleFavorite(room: Room) {
        viewModelScope.launch {
            runCatching { repository.toggleFavorite(room.id) }
                .onSuccess { added ->
                    val messageId = if (added) R.string.favorite_added_format else R.string.favorite_removed_format
                    setMessage(appContext.getString(messageId, room.localizedName(appContext)))
                }
                .onFailure { exception -> setError(appContext.getString(R.string.favorite_error, exceptionMessage(exception))) }
        }
    }

    fun removeFavorite(roomId: String, onRemoved: (FavoriteRoomEntity?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onRemoved(repository.removeFavorite(roomId))
            } catch (exception: Exception) {
                setError(appContext.getString(R.string.favorite_remove_error, exceptionMessage(exception)))
                onRemoved(null)
            }
        }
    }

    fun undoRemoveFavorite(entry: FavoriteRoomEntity) = launchOperation(R.string.favorite_remove_undo) {
        repository.restoreFavorite(entry)
    }

    fun renameFavorite(roomId: String, name: String?) = launchOperation(R.string.favorite_renamed) {
        repository.renameFavorite(roomId, name)
    }

    fun assignCategory(roomId: String, categoryId: Long?) = launchOperation(R.string.favorite_folder_updated) {
        repository.assignCategory(roomId, categoryId)
    }

    fun moveFavorite(roomId: String, direction: Int) = launchOperation {
        repository.moveFavorite(roomId, direction)
    }

    fun createCategory(name: String, colorArgb: Int = 0xFF3157F6.toInt()) = launchOperation(R.string.category_created) {
        repository.createCategory(name, "folder", colorArgb)
    }

    fun renameCategory(id: Long, name: String) = launchOperation(R.string.category_renamed) {
        repository.renameCategory(id, name)
    }

    fun deleteCategory(id: Long) = launchOperation(R.string.category_deleted) {
        repository.deleteCategory(id)
    }

    fun moveCategory(id: Long, direction: Int) = launchOperation {
        repository.moveCategory(id, direction)
    }

    fun recordVisit(roomId: String) = launchOperation {
        repository.recordVisit(roomId)
    }

    fun removeHistoryEntry(id: Long, onRemoved: (RouteHistoryEntity?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onRemoved(repository.removeHistoryEntry(id))
            } catch (exception: Exception) {
                setError(appContext.getString(R.string.history_remove_error, exceptionMessage(exception)))
                onRemoved(null)
            }
        }
    }

    fun undoRemoveHistoryEntry(entry: RouteHistoryEntity) = launchOperation(R.string.history_restored) {
        repository.restoreHistoryEntry(entry)
    }

    fun clearHistory() = launchOperation(R.string.history_cleared) {
        repository.clearHistory()
    }

    fun saveRoute(
        destination: Room,
        fromRoomId: String?,
        fromFloor: Int?,
        distanceMeters: Double?,
        name: String? = null,
    ) = launchOperation(R.string.route_saved) {
        val start = fromRoomId?.let { id -> _state.value.rooms.firstOrNull { it.id == id } }
        val startLabel = when {
            start != null -> start.localizedName(appContext)
            fromFloor != null -> appContext.getString(R.string.floor_number_format, fromFloor)
            else -> appContext.getString(R.string.my_location)
        }
        repository.saveRoute(
            SavedRouteEntity(
                name = name?.trim()?.takeIf(String::isNotEmpty)
                    ?: appContext.getString(R.string.saved_route_name_format, startLabel, destination.localizedName(appContext)),
                fromRoomId = fromRoomId,
                toRoomId = destination.id,
                fromFloor = fromFloor,
                pathLengthMeters = distanceMeters,
            ),
        )
    }

    fun renameSavedRoute(id: Long, name: String) = launchOperation(R.string.route_renamed) {
        repository.renameSavedRoute(id, name)
    }

    fun removeSavedRoute(id: Long, onRemoved: (SavedRouteEntity?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onRemoved(repository.removeSavedRoute(id))
            } catch (exception: Exception) {
                setError(appContext.getString(R.string.saved_route_remove_error, exceptionMessage(exception)))
                onRemoved(null)
            }
        }
    }

    fun undoRemoveSavedRoute(route: SavedRouteEntity) = launchOperation(R.string.route_restored) {
        repository.restoreSavedRoute(route)
    }

    fun exportJson(uri: Uri) = launchOperation(R.string.backup_export_success) {
        repository.exportFavorites(getApplication(), uri)
    }

    fun importJson(uri: Uri) = launchOperation(R.string.backup_import_success) {
        repository.importFavorites(getApplication(), uri)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun launchOperation(@StringRes successMessage: Int? = null, action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            try {
                action()
                successMessage?.let { setMessage(appContext.getString(it)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                setError(exceptionMessage(exception))
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    /** Показывает пользовательские проверки из репозитория, но не выводит сырой текст ошибок Room/SQL. */
    private fun exceptionMessage(exception: Throwable): String = when (exception) {
        is IOException, is IllegalArgumentException -> exception.message?.takeIf(String::isNotBlank)
            ?: appContext.getString(R.string.operation_failed)
        else -> appContext.getString(R.string.operation_failed)
    }

    private fun setMessage(text: String) {
        _state.update { it.copy(message = text, error = null) }
    }

    private fun setError(text: String) {
        _state.update { it.copy(message = null, error = text) }
    }
}
