package com.logos.navigator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.logos.navigator.R
import com.logos.navigator.data.model.AppLanguage
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.DistanceUnit
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.LocationAccuracyMode
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.RoomLabelMode
import com.logos.navigator.data.model.RouteLineStyle
import com.logos.navigator.data.model.RoutePreference
import com.logos.navigator.data.model.SpeechLanguage
import com.logos.navigator.data.model.StartupLocationMode
import com.logos.navigator.data.model.SvgRenderQuality
import com.logos.navigator.data.model.ThemeMode
import com.logos.navigator.utils.AppLocaleController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    floors: List<FloorPlan>,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    var titleTapCount by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val orderedFloors = floors.sortedBy(FloorPlan::number)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable {
                        titleTapCount++
                        if (titleTapCount >= DEVELOPER_TAPS_TO_UNLOCK && !settings.developerOptionsUnlocked) {
                            onUpdateSettings { it.copy(developerOptionsUnlocked = true) }
                            titleTapCount = 0
                        }
                    }) {
                        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_changes_apply), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(title = stringResource(R.string.settings_appearance), icon = Icons.Default.Palette) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = {
                        when (it) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(themeMode = value) } },
                )
                Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ACCENT_COLOR_OPTIONS.forEach { (labelRes, color) ->
                        FilterChip(
                            selected = settings.accentColorArgb == color,
                            onClick = { onUpdateSettings { it.copy(accentColorArgb = color) } },
                            label = { Text(stringResource(labelRes)) },
                            leadingIcon = { ColorDot(color) },
                        )
                    }
                }
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = AppLanguage.entries,
                    selected = settings.appLanguage,
                    label = {
                        when (it) {
                            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                            AppLanguage.RUSSIAN -> stringResource(R.string.language_russian)
                            AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                        }
                    },
                    onSelected = { language ->
                        onUpdateSettings { current -> current.copy(appLanguage = language) }
                        AppLocaleController.apply(language)
                    },
                )
                SliderSetting(
                    title = stringResource(R.string.settings_text_size, (settings.fontScale * 100).toInt()),
                    value = settings.fontScale,
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    onValueChange = { value -> onUpdateSettings { it.copy(fontScale = value) } },
                )
                SwitchSetting(stringResource(R.string.settings_animations), stringResource(R.string.settings_animations_description), settings.animationsEnabled) { checked ->
                    onUpdateSettings { current -> current.copy(animationsEnabled = checked) }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_map), icon = Icons.Default.Map) {
                Text(stringResource(R.string.settings_room_labels), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = RoomLabelMode.entries,
                    selected = settings.roomLabelMode,
                    label = {
                        when (it) {
                            RoomLabelMode.ALWAYS -> stringResource(R.string.labels_always)
                            RoomLabelMode.ON_ZOOM -> stringResource(R.string.labels_on_zoom)
                            RoomLabelMode.NEVER -> stringResource(R.string.labels_never)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(roomLabelMode = value) } },
                )
                Text(stringResource(R.string.settings_initial_floor), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = settings.initialFloor == "last",
                        onClick = { onUpdateSettings { it.copy(initialFloor = "last") } },
                        label = { Text(stringResource(R.string.settings_last_floor)) },
                    )
                    orderedFloors.forEach { floor ->
                        FilterChip(
                            selected = settings.initialFloor == floor.number.toString(),
                            onClick = { onUpdateSettings { it.copy(initialFloor = floor.number.toString()) } },
                            label = { Text(stringResource(R.string.floor_title_format, floor.number)) },
                        )
                    }
                }
                SliderSetting(stringResource(R.string.settings_max_zoom, settings.maximumZoom), settings.maximumZoom, 2f..10f, 7) { value ->
                    onUpdateSettings { current -> current.copy(maximumZoom = value) }
                }
                SwitchSetting(stringResource(R.string.settings_coordinate_grid), stringResource(R.string.settings_grid_description), settings.showCoordinateGrid) { checked ->
                    onUpdateSettings { current -> current.copy(showCoordinateGrid = checked) }
                }
                SwitchSetting(stringResource(R.string.settings_compass), stringResource(R.string.settings_compass_description), settings.compassRotationEnabled) { checked ->
                    onUpdateSettings { current -> current.copy(compassRotationEnabled = checked) }
                }
                Text(stringResource(R.string.settings_svg_quality), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = SvgRenderQuality.entries,
                    selected = settings.svgRenderQuality,
                    label = {
                        when (it) {
                            SvgRenderQuality.LOW -> stringResource(R.string.svg_quality_low)
                            SvgRenderQuality.MEDIUM -> stringResource(R.string.svg_quality_medium)
                            SvgRenderQuality.HIGH -> stringResource(R.string.svg_quality_high)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(svgRenderQuality = value) } },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_navigation), icon = Icons.Default.Navigation) {
                SwitchSetting(stringResource(R.string.settings_auto_route), stringResource(R.string.settings_auto_route_description), settings.automaticRouteOnSelection) { checked ->
                    onUpdateSettings { current -> current.copy(automaticRouteOnSelection = checked) }
                }
                SwitchSetting(stringResource(R.string.settings_arrival_vibration), stringResource(R.string.settings_arrival_vibration_description), settings.arrivalVibrationEnabled) { checked ->
                    onUpdateSettings { current -> current.copy(arrivalVibrationEnabled = checked) }
                }
                SwitchSetting(stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_description), settings.voiceGuidanceEnabled) { checked ->
                    onUpdateSettings { current -> current.copy(voiceGuidanceEnabled = checked) }
                }
                Text(stringResource(R.string.settings_speech_language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = SpeechLanguage.entries,
                    selected = settings.speechLanguage,
                    label = {
                        when (it) {
                            SpeechLanguage.SYSTEM -> stringResource(R.string.speech_language_system)
                            SpeechLanguage.RUSSIAN -> stringResource(R.string.speech_language_russian)
                            SpeechLanguage.ENGLISH -> stringResource(R.string.speech_language_english)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(speechLanguage = value) } },
                )
                SwitchSetting(
                    stringResource(R.string.settings_use_elevator),
                    stringResource(R.string.settings_use_elevator_description),
                    settings.useElevator,
                ) { checked -> onUpdateSettings { it.copy(useElevator = checked) } }
                Text(stringResource(R.string.settings_route_preference), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = RoutePreference.entries,
                    selected = settings.routePreference,
                    label = {
                        when (it) {
                            RoutePreference.SHORTEST -> stringResource(R.string.route_shortest)
                            RoutePreference.AVOID_STAIRS -> stringResource(R.string.route_avoid_stairs)
                            RoutePreference.AVOID_ELEVATOR -> stringResource(R.string.route_avoid_elevator)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(routePreference = value) } },
                )
                Text(stringResource(R.string.settings_distance_unit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = DistanceUnit.entries,
                    selected = settings.distanceUnit,
                    label = { if (it == DistanceUnit.METERS) stringResource(R.string.unit_meters) else stringResource(R.string.unit_steps) },
                    onSelected = { value -> onUpdateSettings { it.copy(distanceUnit = value) } },
                )
                SliderSetting(stringResource(R.string.settings_walking_speed, settings.walkingSpeedKmh), settings.walkingSpeedKmh, 2f..7f, 9) { value ->
                    onUpdateSettings { current -> current.copy(walkingSpeedKmh = value) }
                }
                Text(stringResource(R.string.settings_route_style), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = RouteLineStyle.entries,
                    selected = settings.routeLineStyle,
                    label = {
                        when (it) {
                            RouteLineStyle.SOLID -> stringResource(R.string.route_style_solid)
                            RouteLineStyle.DASHED -> stringResource(R.string.route_style_dashed)
                            RouteLineStyle.ANIMATED -> stringResource(R.string.route_style_animated)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(routeLineStyle = value) } },
                )
                Text(stringResource(R.string.settings_route_color), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ROUTE_COLOR_OPTIONS.forEach { (labelRes, color) ->
                        FilterChip(
                            selected = settings.routeColorArgb == color,
                            onClick = { onUpdateSettings { it.copy(routeColorArgb = color) } },
                            label = { Text(stringResource(labelRes)) },
                            leadingIcon = { ColorDot(color) },
                        )
                    }
                }
                SliderSetting(stringResource(R.string.settings_route_width, settings.routeLineWidthDp), settings.routeLineWidthDp, 2f..10f, 7) { value ->
                    onUpdateSettings { current -> current.copy(routeLineWidthDp = value) }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_location), icon = Icons.Default.LocationOn) {
                Text(stringResource(R.string.settings_startup_location), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = StartupLocationMode.entries,
                    selected = settings.startupLocationMode,
                    label = {
                        when (it) {
                            StartupLocationMode.GPS -> stringResource(R.string.startup_gps)
                            StartupLocationMode.MANUAL -> stringResource(R.string.startup_manual)
                            StartupLocationMode.LAST -> stringResource(R.string.startup_last)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(startupLocationMode = value) } },
                )
                SwitchSetting(stringResource(R.string.settings_gps_on_launch), stringResource(R.string.settings_gps_on_launch_description), settings.gpsEnabledOnLaunch) { checked ->
                    onUpdateSettings { current -> current.copy(gpsEnabledOnLaunch = checked) }
                }
                Text(stringResource(R.string.settings_gps_quality), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ChoiceRow(
                    values = LocationAccuracyMode.entries,
                    selected = settings.locationAccuracyMode,
                    label = {
                        when (it) {
                            LocationAccuracyMode.HIGH -> stringResource(R.string.gps_accuracy_high)
                            LocationAccuracyMode.BALANCED -> stringResource(R.string.gps_accuracy_balanced)
                            LocationAccuracyMode.BATTERY -> stringResource(R.string.gps_accuracy_battery)
                        }
                    },
                    onSelected = { value -> onUpdateSettings { it.copy(locationAccuracyMode = value) } },
                )
                SliderSetting(stringResource(R.string.settings_location_interval, settings.locationUpdateIntervalSeconds), settings.locationUpdateIntervalSeconds.toFloat(), 1f..10f, 8) { value ->
                    onUpdateSettings { current -> current.copy(locationUpdateIntervalSeconds = value.toInt()) }
                }
                SliderSetting(stringResource(R.string.settings_min_accuracy, settings.minimumAccuracyMeters.toInt()), settings.minimumAccuracyMeters, 5f..50f, 8) { value ->
                    onUpdateSettings { current -> current.copy(minimumAccuracyMeters = value) }
                }
                SwitchSetting(stringResource(R.string.settings_accuracy_circle), stringResource(R.string.settings_accuracy_circle_description), settings.showAccuracyCircle) { checked ->
                    onUpdateSettings { current -> current.copy(showAccuracyCircle = checked) }
                }
                SwitchSetting(stringResource(R.string.settings_manual_fallback), stringResource(R.string.settings_manual_fallback_description), settings.switchToManualOnPoorGps) { checked ->
                    onUpdateSettings { current -> current.copy(switchToManualOnPoorGps = checked) }
                }
                Text(
                    stringResource(R.string.gps_caveat),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onOpenAppPermissions, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Text(stringResource(R.string.settings_permissions))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_favorites_history), icon = Icons.Default.Favorite) {
                SwitchSetting(stringResource(R.string.settings_history_enabled), stringResource(R.string.settings_history_description), settings.saveRouteHistory) { checked ->
                    onUpdateSettings { current -> current.copy(saveRouteHistory = checked) }
                }
                SliderSetting(stringResource(R.string.settings_history_limit, settings.maximumHistoryRecords), settings.maximumHistoryRecords.toFloat(), 10f..100f, 17) { value ->
                    onUpdateSettings { current -> current.copy(maximumHistoryRecords = value.toInt()) }
                }
                OutlinedButton(onClick = onOpenFavorites, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                    Text(stringResource(R.string.manage_favorites))
                }
            }

            SettingsSection(title = stringResource(R.string.settings_about), icon = Icons.Default.Info) {
                Text(stringResource(R.string.organization_name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Text(stringResource(R.string.licenses_info))
                }
                OutlinedButton(
                    onClick = onReplayOnboarding,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_replay_tutorial)) }
            }

            if (settings.developerOptionsUnlocked) {
                SettingsSection(title = stringResource(R.string.developer_options), icon = Icons.Default.DeveloperMode) {
                    Text(stringResource(R.string.developer_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchSetting(stringResource(R.string.developer_tap_coordinates), stringResource(R.string.developer_coordinates_description), settings.showTapCoordinates) { checked ->
                        onUpdateSettings { current -> current.copy(showTapCoordinates = checked) }
                    }
                    SwitchSetting(stringResource(R.string.developer_nodes), stringResource(R.string.developer_nodes_description), settings.showNavigationNodes) { checked ->
                        onUpdateSettings { current -> current.copy(showNavigationNodes = checked) }
                    }
                    SwitchSetting(stringResource(R.string.developer_edges), stringResource(R.string.developer_edges_description), settings.showNavigationEdges) { checked ->
                        onUpdateSettings { current -> current.copy(showNavigationEdges = checked) }
                    }
                    SwitchSetting(stringResource(R.string.developer_doors), stringResource(R.string.developer_doors_description), settings.showDoorNodes) { checked ->
                        onUpdateSettings { current -> current.copy(showDoorNodes = checked) }
                    }
                    SwitchSetting(stringResource(R.string.developer_logging), stringResource(R.string.developer_logging_description), settings.fileLoggingEnabled) { checked ->
                        onUpdateSettings { current -> current.copy(fileLoggingEnabled = checked) }
                    }
                    TextButton(onClick = { onUpdateSettings { it.copy(developerOptionsUnlocked = false) } }) { Text(stringResource(R.string.developer_hide)) }
                }
            }

            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text(stringResource(R.string.reset_settings))
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_settings_title)) },
            text = { Text(stringResource(R.string.reset_settings_message)) },
            confirmButton = {
                Button(onClick = { onResetSettings(); confirmReset = false }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Slider(value = value.coerceIn(valueRange), onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun ColorDot(colorArgb: Int) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(14.dp)
            .background(Color(colorArgb), androidx.compose.foundation.shape.CircleShape),
    )
}

private val ACCENT_COLOR_OPTIONS = listOf(
    R.string.accent_logos to 0xFF2538F5.toInt(),
    R.string.accent_green to 0xFF16856B.toInt(),
    R.string.accent_orange to 0xFFF07B2B.toInt(),
    R.string.accent_purple to 0xFF7754C8.toInt(),
    R.string.accent_red to 0xFFC43E54.toInt(),
)

private val ROUTE_COLOR_OPTIONS = listOf(
    R.string.route_color_blue to 0xFF2538F5.toInt(),
    R.string.route_color_orange to 0xFFF07B2B.toInt(),
    R.string.route_color_green to 0xFF16856B.toInt(),
    R.string.route_color_red to 0xFFC43E54.toInt(),
    R.string.route_color_purple to 0xFF7754C8.toInt(),
)

private const val DEVELOPER_TAPS_TO_UNLOCK = 5
