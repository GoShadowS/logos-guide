package com.logos.navigator.ui.favorites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import com.logos.navigator.data.database.entity.FavoriteCategoryEntity
import com.logos.navigator.data.database.entity.FavoriteRoomEntity
import com.logos.navigator.data.database.entity.RouteHistoryEntity
import com.logos.navigator.R
import com.logos.navigator.data.database.entity.SavedRouteEntity
import com.logos.navigator.data.model.Room
import com.logos.navigator.utils.categoryLabel
import com.logos.navigator.utils.formatDistance
import com.logos.navigator.utils.localizedDescription
import com.logos.navigator.utils.localizedName
import com.logos.navigator.utils.roomSearchScore
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FavoriteTab(val labelRes: Int) {
    ROOMS(R.string.favorite_tab_rooms), HISTORY(R.string.favorite_tab_history), ROUTES(R.string.favorite_tab_routes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRoom: (Room) -> Unit,
    onRepeatRoute: (fromRoomId: String?, toRoomId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val favoriteRemovedLabel = stringResource(R.string.favorite_removed)
    val historyDeletedLabel = stringResource(R.string.history_deleted)
    val routeDeletedLabel = stringResource(R.string.route_deleted)
    var selectedTab by rememberSaveable { mutableStateOf(FavoriteTab.ROOMS.ordinal) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_CATEGORY_KEY) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<FavoriteCategoryEntity?>(null) }
    var renameFavorite by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameRoute by remember { mutableStateOf<SavedRouteEntity?>(null) }
    var showClearHistory by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importJson)
    }

    LaunchedEffect(state.message, state.error) {
        val message = state.error ?: state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearMessage()
    }

    fun removeFavorite(roomId: String) {
        viewModel.removeFavorite(roomId) { removed ->
            if (removed != null) scope.launch {
                val result = snackbar.showSnackbar(
                    message = favoriteRemovedLabel,
                    actionLabel = undoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.undoRemoveFavorite(removed)
                }
            }
        }
    }

    val favoritesWithRooms = remember(state.favoriteRooms, state.rooms, query, selectedCategory, context) {
        state.favoriteRooms.mapNotNull { favorite ->
            state.rooms.firstOrNull { it.id == favorite.roomId }?.let { favorite to it }
        }.filter { (favorite, room) ->
            val categoryMatches = when (selectedCategory) {
                ALL_CATEGORY_KEY -> true
                UNCATEGORIZED_KEY -> favorite.categoryId == null
                else -> favorite.categoryId?.toString() == selectedCategory
            }
            val searchableValues = buildList {
                add(favorite.customName.orEmpty())
                add(room.localizedName(context))
                add(room.name)
                add(room.nameEn.orEmpty())
                add(room.number.orEmpty())
                add(room.localizedDescription(context).orEmpty())
                add(room.description.orEmpty())
                add(room.descriptionEn.orEmpty())
                add(context.categoryLabel(room.category))
                addAll(room.aliasesRu)
                addAll(room.aliasesEn)
            }
            categoryMatches && roomSearchScore(query, room.number, searchableValues) != null
        }
    }
    val historyEntries = remember(state.history, state.rooms, query, context) {
        val roomsById = state.rooms.associateBy(Room::id)
        state.history.filter { entry ->
            val relatedRooms = listOfNotNull(entry.fromRoomId?.let(roomsById::get), roomsById[entry.toRoomId])
            val searchableValues = buildList {
                add(entry.fromLabel.orEmpty())
                relatedRooms.forEach { room ->
                    add(room.localizedName(context))
                    add(room.name)
                    add(room.nameEn.orEmpty())
                    add(room.number.orEmpty())
                    add(room.localizedDescription(context).orEmpty())
                    add(room.description.orEmpty())
                    add(room.descriptionEn.orEmpty())
                    addAll(room.aliasesRu)
                    addAll(room.aliasesEn)
                }
            }
            roomSearchScore(query, null, searchableValues) != null
        }
    }
    val savedEntries = remember(state.savedRoutes, state.rooms, query, context) {
        val roomsById = state.rooms.associateBy(Room::id)
        state.savedRoutes.filter { route ->
            val relatedRooms = listOfNotNull(route.fromRoomId?.let(roomsById::get), roomsById[route.toRoomId])
            val searchableValues = buildList {
                add(route.name)
                relatedRooms.forEach { room ->
                    add(room.localizedName(context))
                    add(room.name)
                    add(room.nameEn.orEmpty())
                    add(room.number.orEmpty())
                    addAll(room.aliasesRu)
                    addAll(room.aliasesEn)
                }
            }
            roomSearchScore(query, null, searchableValues) != null
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.favorites_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.favorites_local_only), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("logos-favorites.json") }) {
                        Icon(Icons.Default.ImportExport, contentDescription = stringResource(R.string.backup_export))
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.backup_import))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                FavoriteTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.search_list)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
                if (selectedTab == FavoriteTab.ROOMS.ordinal) {
                    IconButton(onClick = { showCreateCategory = true }) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.create_folder))
                    }
                }
                if (selectedTab == FavoriteTab.HISTORY.ordinal) {
                    IconButton(onClick = { showClearHistory = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_history))
                    }
                }
            }

            when (selectedTab) {
                FavoriteTab.ROOMS.ordinal -> {
                    CategoryFilterBar(
                        categories = state.categories,
                        selectedCategory = selectedCategory,
                        onSelect = { selectedCategory = it },
                        onCreate = { showCreateCategory = true },
                        selectedEntity = state.categories.firstOrNull { it.id.toString() == selectedCategory },
                        onEdit = { editCategory = it },
                        onMove = viewModel::moveCategory,
                        onDelete = viewModel::deleteCategory,
                    )
                    if (favoritesWithRooms.isEmpty()) {
                        EmptyPanel(
                            title = if (state.favoriteRooms.isEmpty()) stringResource(R.string.no_favorites) else stringResource(R.string.no_favorites_results),
                            description = stringResource(R.string.favorites_empty_description),
                            actionLabel = stringResource(R.string.find_room),
                            onAction = onOpenSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(favoritesWithRooms, key = { it.first.roomId }) { (favorite, room) ->
                                SwipeDeleteFavorite(
                                    favorite = favorite,
                                    room = room,
                                    categories = state.categories,
                                    onClick = { onOpenRoom(room) },
                                    onDelete = { removeFavorite(favorite.roomId) },
                                    onRename = { renameFavorite = favorite.roomId to (favorite.customName ?: room.localizedName(context)) },
                                    onAssignCategory = { categoryId -> viewModel.assignCategory(favorite.roomId, categoryId) },
                                    onMove = { direction -> viewModel.moveFavorite(favorite.roomId, direction) },
                                )
                            }
                        }
                    }
                }
                FavoriteTab.HISTORY.ordinal -> {
                    if (historyEntries.isEmpty()) {
                        EmptyPanel(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_description), null, null, Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(historyEntries, key = RouteHistoryEntity::id) { entry ->
                                HistoryCard(
                                    entry = entry,
                                    destination = state.rooms.firstOrNull { it.id == entry.toRoomId },
                                    onRepeat = { onRepeatRoute(entry.fromRoomId, entry.toRoomId) },
                                    onDelete = {
                                        viewModel.removeHistoryEntry(entry.id) { removed ->
                                            if (removed != null) scope.launch {
                                                val result = snackbar.showSnackbar(
                                                    historyDeletedLabel,
                                                    actionLabel = undoLabel,
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                    viewModel.undoRemoveHistoryEntry(removed)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (savedEntries.isEmpty()) {
                        EmptyPanel(stringResource(R.string.saved_routes_empty_title), stringResource(R.string.saved_routes_empty_description), null, null, Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(savedEntries, key = SavedRouteEntity::id) { route ->
                                SavedRouteCard(
                                    route = route,
                                    onRepeat = { onRepeatRoute(route.fromRoomId, route.toRoomId) },
                                    onRename = { renameRoute = route },
                                    onDelete = {
                                        viewModel.removeSavedRoute(route.id) { removed ->
                                            if (removed != null) scope.launch {
                                                val result = snackbar.showSnackbar(
                                                    routeDeletedLabel,
                                                    actionLabel = undoLabel,
                                                    withDismissAction = true,
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                    viewModel.undoRemoveSavedRoute(removed)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (state.isWorking) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }

    if (showCreateCategory) {
        NameDialog(
            title = stringResource(R.string.new_folder),
            initialValue = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showCreateCategory = false },
            onConfirm = { value -> viewModel.createCategory(value); showCreateCategory = false },
        )
    }
    editCategory?.let { category ->
        NameDialog(
            title = stringResource(R.string.rename_folder_title),
            initialValue = category.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { editCategory = null },
            onConfirm = { value -> viewModel.renameCategory(category.id, value); editCategory = null },
        )
    }
    renameFavorite?.let { (roomId, initialName) ->
        NameDialog(
            title = stringResource(R.string.custom_name_title),
            initialValue = initialName,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renameFavorite = null },
            onConfirm = { value -> viewModel.renameFavorite(roomId, value); renameFavorite = null },
        )
    }
    renameRoute?.let { route ->
        NameDialog(
            title = stringResource(R.string.saved_route_name_title),
            initialValue = route.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renameRoute = null },
            onConfirm = { value -> viewModel.renameSavedRoute(route.id, value); renameRoute = null },
        )
    }
    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            confirmButton = {
                Button(onClick = { viewModel.clearHistory(); showClearHistory = false }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CategoryFilterBar(
    categories: List<FavoriteCategoryEntity>,
    selectedCategory: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    selectedEntity: FavoriteCategoryEntity?,
    onEdit: (FavoriteCategoryEntity) -> Unit,
    onMove: (Long, Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item(key = ALL_CATEGORY_KEY) {
                FilterChip(selectedCategory == ALL_CATEGORY_KEY, onClick = { onSelect(ALL_CATEGORY_KEY) }, label = { Text(stringResource(R.string.all_favorites)) })
            }
            item(key = UNCATEGORIZED_KEY) {
                FilterChip(selectedCategory == UNCATEGORIZED_KEY, onClick = { onSelect(UNCATEGORIZED_KEY) }, label = { Text(stringResource(R.string.uncategorized)) })
            }
            items(categories, key = FavoriteCategoryEntity::id) { category ->
                FilterChip(
                    selected = selectedCategory == category.id.toString(),
                    onClick = { onSelect(category.id.toString()) },
                    label = { Text(category.name) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selectedCategory == ALL_CATEGORY_KEY) stringResource(R.string.favorite_folders) else selectedEntity?.name ?: stringResource(R.string.uncategorized),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedEntity != null) {
                IconButton(onClick = { onMove(selectedEntity.id, -1) }) { Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.move_folder_up)) }
                IconButton(onClick = { onMove(selectedEntity.id, 1) }) { Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.move_folder_down)) }
                IconButton(onClick = { onEdit(selectedEntity) }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_folder_title)) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_actions)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_folder)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete(selectedEntity.id) },
                        )
                    }
                }
            } else {
                IconButton(onClick = onCreate) { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.create_folder)) }
            }
        }
    }
}

@Composable
private fun SwipeDeleteFavorite(
    favorite: FavoriteRoomEntity,
    room: Room,
    categories: List<FavoriteCategoryEntity>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onAssignCategory: (Long?) -> Unit,
    onMove: (Int) -> Unit,
) {
    var swipeOffset by remember(favorite.roomId) { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val currentCategory = categories.firstOrNull { it.id == favorite.categoryId }
    val context = LocalContext.current
    val cabinetLabel = room.number?.let { stringResource(R.string.cabinet_number_format, it) }
    val floorLabel = stringResource(R.string.floor_number_format, room.floor)
    val roomMetadata = if (cabinetLabel == null) {
        stringResource(R.string.room_floor_category_only, floorLabel, context.categoryLabel(room.category))
    } else {
        stringResource(R.string.room_number_floor_category, cabinetLabel, floorLabel, context.categoryLabel(room.category))
    }
    val metadata = currentCategory?.let { category ->
        stringResource(R.string.favorite_room_metadata_with_folder, roomMetadata, category.name)
    } ?: roomMetadata

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .pointerInput(favorite.roomId) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-220f, 0f)
                        },
                        onDragEnd = {
                            if (swipeOffset < -88f) onDelete()
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                    )
                }
                .pointerInput(favorite.roomId) {
                    var verticalDrag = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { verticalDrag = 0f },
                        onDrag = { change, delta ->
                            verticalDrag += delta.y
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                verticalDrag < -45f -> onMove(-1)
                                verticalDrag > 45f -> onMove(1)
                            }
                        },
                        onDragCancel = { verticalDrag = 0f },
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 3.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(room.number?.filter(Char::isDigit)?.takeLast(3)?.ifBlank { "•" } ?: "•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        favorite.customName?.takeIf(String::isNotBlank) ?: room.localizedName(context),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClick) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.open_room)) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.favorite_actions)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_folder)) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = { menuExpanded = false; categoryMenuExpanded = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_up)) },
                            leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove(-1) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_down)) },
                            leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove(1) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uncategorized)) },
                            onClick = { categoryMenuExpanded = false; onAssignCategory(null) },
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { categoryMenuExpanded = false; onAssignCategory(category.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: RouteHistoryEntity,
    destination: Room?,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val destinationLabel = destination?.number?.let { stringResource(R.string.cabinet_number_format, it) }
        ?: destination?.let { stringResource(R.string.floor_number_format, it.floor) }
        ?: stringResource(R.string.deleted_destination)
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(destination?.localizedName(context) ?: stringResource(R.string.deleted_destination), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.history_route_description, entry.fromLabel ?: stringResource(R.string.from_my_location), destinationLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${context.formatDistance(entry.pathLengthMeters)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRepeat, enabled = destination != null) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.repeat))
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_history_entry)) }
            }
        }
    }
}

@Composable
private fun SavedRouteCard(
    route: SavedRouteEntity,
    onRepeat: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val distanceLabel = route.pathLengthMeters?.let { context.formatDistance(it) } ?: stringResource(R.string.route_length_recalculating)
    val routeMetadata = route.fromFloor?.let { floor ->
        stringResource(R.string.saved_route_metadata_format, stringResource(R.string.floor_title_format, floor), distanceLabel)
    } ?: distanceLabel
    var menuExpanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(route.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.saved_route_actions)) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuExpanded = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }
            Text(
                routeMetadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRepeat) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.repeat_route))
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(60) },
                singleLine = true,
                label = { Text(stringResource(R.string.route_name_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { Button(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EmptyPanel(
    title: String,
    description: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private const val ALL_CATEGORY_KEY = "all"
private const val UNCATEGORIZED_KEY = "none"
