package com.logos.navigator.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logos.navigator.R
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.Room
import com.logos.navigator.utils.categoryLabel
import com.logos.navigator.utils.localizedDescription
import com.logos.navigator.utils.localizedName
import com.logos.navigator.utils.matchingNumberLabel
import com.logos.navigator.utils.normalizeSearchText
import com.logos.navigator.utils.numberLabels
import com.logos.navigator.utils.roomSearchScore

private data class CategoryFilter(val id: String, val titleRes: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    rooms: List<Room>,
    floors: List<FloorPlan>,
    favoriteRoomIds: Set<String> = emptySet(),
    onToggleFavorite: (Room) -> Unit = {},
    onBack: () -> Unit,
    onSelectRoom: (Room) -> Unit,
    onBuildRoute: ((Room) -> Unit)? = null,
    onVoiceQuickAction: ((String) -> Unit)? = null,
    initialQuery: String = "",
    initialCategory: String = ALL_CATEGORIES,
    searchRequestVersion: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var floorFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var appliedSearchRequestVersion by rememberSaveable { mutableIntStateOf(-1) }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                category = ALL_CATEGORIES
                floorFilter = null
                val normalized = normalizeSearchText(spoken)
                val quickAction = when {
                    listOf("ближайший туалет", "ближайшая уборная", "nearest restroom", "nearest toilet")
                        .any { normalized.contains(normalizeSearchText(it)) } -> "restroom"
                    listOf("ближайшая лестница", "nearest stairs", "nearest staircase")
                        .any { normalized.contains(normalizeSearchText(it)) } -> "stairs"
                    else -> null
                }
                if (quickAction != null && onVoiceQuickAction != null) {
                    onVoiceQuickAction(quickAction)
                } else {
                    val cleanQuery = cleanVoiceSearchCommand(spoken)
                    query = cleanQuery.ifBlank { spoken }
                    val isRouteCommand = listOf(
                        "маршрут", "проложи", "построй", "navigate", "route to", "build route", "build a route", "take me to",
                    )
                        .any { normalized.contains(normalizeSearchText(it)) }
                    if (isRouteCommand) {
                        exactVoiceDestination(rooms, query, context)?.let { room -> onBuildRoute?.invoke(room) }
                    }
                }
            }
        }
    }

    fun startVoiceSearch() {
        val locale = context.resources.configuration.locales
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?: java.util.Locale.getDefault()
        val languageTag = if (locale.language == "en") "en-US" else "ru-RU"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.voice_search_error, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(searchRequestVersion, initialQuery, initialCategory) {
        if (appliedSearchRequestVersion == searchRequestVersion) return@LaunchedEffect
        query = initialQuery
        category = initialCategory
        floorFilter = null
        appliedSearchRequestVersion = searchRequestVersion
    }

    val categoryFilters = remember {
        listOf(
            CategoryFilter(ALL_CATEGORIES, R.string.all_categories),
            CategoryFilter("classroom", R.string.category_classrooms),
            CategoryFilter("laboratory", R.string.category_laboratories),
            CategoryFilter("administrative", R.string.category_administrative),
            CategoryFilter("medical", R.string.category_medical),
            CategoryFilter("common", R.string.category_common),
            CategoryFilter("entrance", R.string.category_entrance),
        )
    }
    val matchingRooms = remember(rooms, query, category, floorFilter, context) {
        rooms.asSequence()
            .filter { category == ALL_CATEGORIES || it.category.equals(category, ignoreCase = true) }
            .filter { floorFilter == null || it.floor == floorFilter }
            .mapNotNull { room ->
                val searchableValues = buildList {
                    add(room.localizedName(context))
                    add(room.name)
                    add(room.nameEn.orEmpty())
                    add(room.number.orEmpty())
                    room.number?.let {
                        add(context.getString(R.string.cabinet_number_format, it))
                        add("каб $it")
                        add("room $it")
                    }
                    add(room.localizedDescription(context).orEmpty())
                    add(room.description.orEmpty())
                    add(room.descriptionEn.orEmpty())
                    add(context.categoryLabel(room.category))
                    add(context.getString(R.string.floor_title_format, room.floor))
                    add(context.getString(R.string.floor_number_format, room.floor))
                    addAll(room.aliasesRu)
                    addAll(room.aliasesEn)
                }
                val score = roomSearchScore(query, room.number, searchableValues) ?: return@mapNotNull null
                room to score
            }
            .sortedWith(
                compareByDescending<Pair<Room, Int>> { it.second }
                    .thenBy { it.first.floor }
                    .thenBy { it.first.number.orEmpty().filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.first.name },
            )
            .map { it.first }
            .toList()
    }
    val relatedQueries = remember(query, matchingRooms, context) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val isEnglish = context.resources.configuration.locales
                .takeIf { !it.isEmpty }
                ?.get(0)
                ?.language == "en"
            val suggestions = matchingRooms.take(3).flatMap { room ->
                val name = room.localizedName(context)
                buildList {
                    room.matchingNumberLabel(query)?.let { number ->
                        add("${context.getString(R.string.cabinet_number_format, number)} · $name")
                    }
                    val aliases = if (isEnglish) room.aliasesEn else room.aliasesRu
                    aliases.firstOrNull { alias ->
                        alias.isNotBlank() && roomSearchScore(query, null, listOf(alias)) != null
                    }?.let(::add)
                }
            }
            suggestions
                .filter { normalizeSearchText(it) != normalizeSearchText(query) }
                .distinct()
                .take(5)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { startVoiceSearch() }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = stringResource(R.string.voice_search_start),
                                    )
                                }
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                                    }
                                }
                            }
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryFilters, key = CategoryFilter::id) { filter ->
                    FilterChip(
                        selected = category == filter.id,
                        onClick = { category = filter.id },
                        label = { Text(stringResource(filter.titleRes)) },
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = ALL_FLOORS_KEY) {
                    FilterChip(
                        selected = floorFilter == null,
                        onClick = { floorFilter = null },
                        label = { Text(stringResource(R.string.all_floors)) },
                    )
                }
                items(floors.sortedBy(FloorPlan::number), key = FloorPlan::number) { floor ->
                    FilterChip(
                        selected = floorFilter == floor.number,
                        onClick = { floorFilter = floor.number },
                        label = { Text(stringResource(R.string.floor_title_format, floor.number)) },
                    )
                }
            }
            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.search_results_count, matchingRooms.size)
                } else {
                    stringResource(R.string.search_matches_count, matchingRooms.size)
                },
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (matchingRooms.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.no_results_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.no_results_body),
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(matchingRooms, key = { room -> room.id }) { room ->
                        RoomListCard(
                            room = room,
                            isFavorite = room.id in favoriteRoomIds,
                            showRouteAction = true,
                            onClick = { onSelectRoom(room) },
                            onBuildRoute = { if (onBuildRoute != null) onBuildRoute(room) else onSelectRoom(room) },
                            onToggleFavorite = { onToggleFavorite(room) },
                        )
                    }
                    if (relatedQueries.isNotEmpty()) {
                        item(key = RELATED_QUERIES_HEADER_KEY) {
                            Text(
                                stringResource(R.string.search_similar_queries),
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(relatedQueries, key = { "suggestion:$it" }) { suggestion ->
                            RelatedQueryRow(query = suggestion, onClick = { query = suggestion })
                        }
                    }
                }
            }
        }
    }
}

private fun cleanVoiceSearchCommand(spoken: String): String {
    var query = spoken.trim()
    val prefixes = listOf(
        Regex("^(?:(?:построй|проложи)\\s+)?маршрут(?:\\s+(?:до|к|в))?\\s+", RegexOption.IGNORE_CASE),
        Regex("^(?:navigate\\s+to|route\\s+to|take\\s+me\\s+to|build(?:\\s+me)?\\s+(?:a\\s+)?route\\s+to)\\s+", RegexOption.IGNORE_CASE),
        Regex("^(?:найди|покажи|открой|find|show|open)\\s+", RegexOption.IGNORE_CASE),
        Regex("^(?:кабинет(?:а|у|е)?|комнат(?:а|у|ы)?|room|cabinet|office)\\s+(?:(?:номер|number|no\\.?)\\s+)?", RegexOption.IGNORE_CASE),
    )
    prefixes.forEach { prefix -> query = query.replaceFirst(prefix, "") }
    return query.trim().trimEnd('.', ',', '!', '?', ';', ':')
}

private fun exactVoiceDestination(rooms: List<Room>, query: String, context: Context): Room? {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isBlank()) return null
    val exactMatches = rooms.filter { room ->
        room.routeAvailable && !room.nodeId.isNullOrBlank() &&
            (room.numberLabels() + listOf(room.localizedName(context), room.name, room.nameEn.orEmpty()) + room.aliasesRu + room.aliasesEn)
                .any { normalizeSearchText(it) == normalizedQuery }
    }
    return exactMatches.singleOrNull()
}

@Composable
private fun RoomListCard(
    room: Room,
    isFavorite: Boolean,
    showRouteAction: Boolean,
    onClick: () -> Unit,
    onBuildRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val roomNumbers = room.numberLabels()
    val number = roomNumbers.firstOrNull()
    val badge = number?.let { if (roomNumbers.size > 1) "$it+" else it } ?: "•"
    val roomLabel = number?.let { stringResource(R.string.cabinet_number_format, roomNumbers.joinToString(", ")) }.orEmpty()
    val metadata = if (roomLabel.isBlank()) {
        stringResource(R.string.room_floor_category_only, stringResource(R.string.floor_number_format, room.floor), context.categoryLabel(room.category))
    } else {
        stringResource(
            R.string.room_number_floor_category,
            roomLabel,
            stringResource(R.string.floor_number_format, room.floor),
            context.categoryLabel(room.category),
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        room.localizedName(context),
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
                    room.localizedDescription(context)?.let { description ->
                        Text(
                            description,
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showRouteAction && room.routeAvailable && !room.nodeId.isNullOrBlank()) {
                Button(
                    onClick = onBuildRoute,
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.search_build_route))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Navigation, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun RelatedQueryRow(query: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(query, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val ALL_CATEGORIES = "all"
private const val ALL_FLOORS_KEY = "all-floors"
private const val RELATED_QUERIES_HEADER_KEY = "related-queries-header"
