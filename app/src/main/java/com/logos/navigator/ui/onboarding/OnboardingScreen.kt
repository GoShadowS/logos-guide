package com.logos.navigator.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.logos.navigator.R
import com.logos.navigator.data.model.AppLanguage
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Первичный мастер настройки приложения и обучения основным действиям. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    useElevator: Boolean,
    onLanguageSelected: (AppLanguage) -> Unit,
    onUseElevatorChanged: (Boolean) -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    onSkipLocation: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageCount = 10
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    var permissionDecisionMade by rememberSaveable {
        mutableStateOf(hasLocationPermission(context))
    }
    var showPermissionHelp by rememberSaveable { mutableStateOf(false) }
    var deniedPermission by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission(context)
        permissionDecisionMade = true
        deniedPermission = !granted
        onLocationPermissionResult(granted)
        if (!granted) showPermissionHelp = true
    }

    fun requestLocationPermission() {
        if (hasLocationPermission(context)) {
            permissionDecisionMade = true
            deniedPermission = false
            onLocationPermissionResult(true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.onboarding_page_format, pagerState.currentPage + 1, pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = (pagerState.currentPage + 1).toFloat() / pageCount,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    userScrollEnabled = pagerState.currentPage != 2 || permissionDecisionMade,
                ) { page ->
                    OnboardingPage(
                        page = page,
                        useElevator = useElevator,
                        onLanguageSelected = onLanguageSelected,
                        onUseElevatorChanged = onUseElevatorChanged,
                        onRequestLocation = ::requestLocationPermission,
                        permissionGranted = hasLocationPermission(context),
                        permissionWasDenied = deniedPermission,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pageCount) { index ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (pagerState.currentPage == index) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (pagerState.currentPage == 2) {
                        TextButton(onClick = {
                            onSkipLocation()
                            permissionDecisionMade = true
                            scope.launch { pagerState.animateScrollToPage(3) }
                        }) { Text(stringResource(R.string.skip)) }
                    } else if (pagerState.currentPage < pageCount - 1) {
                        TextButton(onClick = {
                            val targetPage = if (pagerState.currentPage < 2) 2 else pageCount - 1
                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                        }) { Text(stringResource(R.string.skip)) }
                    }
                    if (pagerState.currentPage == pageCount - 1) {
                        Button(onClick = onComplete, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.onboarding_start))
                        }
                    } else {
                        Button(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            enabled = pagerState.currentPage != 2 || permissionDecisionMade,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.continue_label)) }
                    }
                }
            }
    }

    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false },
            title = { Text(stringResource(R.string.onboarding_permission_denied_title)) },
            text = { Text(stringResource(R.string.onboarding_permission_denied_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionHelp = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    }
                }) { Text(stringResource(R.string.onboarding_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { showPermissionHelp = false }) { Text(stringResource(R.string.location_manual)) } },
        )
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    useElevator: Boolean,
    onLanguageSelected: (AppLanguage) -> Unit,
    onUseElevatorChanged: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    permissionGranted: Boolean,
    permissionWasDenied: Boolean,
) {
    val pageText = when (page) {
        0 -> R.string.onboarding_welcome_title
        1 -> R.string.onboarding_language_title
        2 -> R.string.onboarding_location_title
        3 -> R.string.onboarding_map_title
        4 -> R.string.onboarding_modes_title
        5 -> R.string.onboarding_route_title
        6 -> R.string.onboarding_elevator_title
        7 -> R.string.onboarding_favorites_title
        8 -> R.string.onboarding_settings_title
        else -> R.string.onboarding_done_title
    }
    val icon = when (page) {
        1 -> Icons.Default.Language
        2 -> Icons.Default.LocationOn
        3 -> Icons.Default.Map
        4, 5 -> Icons.Default.Navigation
        7 -> Icons.Default.Favorite
        8 -> Icons.Default.Settings
        else -> Icons.Default.CheckCircle
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == 0) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = stringResource(R.string.splash_description),
                modifier = Modifier.size(132.dp).clip(CircleShape),
            )
        } else {
            Surface(
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (page == 6) {
                        Text("♿", style = MaterialTheme.typography.displaySmall)
                    } else if (page == 9) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Text(
            text = stringResource(pageText),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        when (page) {
            0 -> {
                Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_welcome_credit), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLanguageSelected(AppLanguage.SYSTEM) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_system)) }
                OutlinedButton(onClick = { onLanguageSelected(AppLanguage.RUSSIAN) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_russian_flag)) }
                OutlinedButton(onClick = { onLanguageSelected(AppLanguage.ENGLISH) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_english_flag)) }
            }
            2 -> {
                Text(stringResource(R.string.onboarding_location_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_location_manual_note), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (permissionGranted) {
                    Text(stringResource(R.string.onboarding_permission_granted), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                } else {
                    Button(onClick = onRequestLocation) { Text(stringResource(R.string.onboarding_allow_location)) }
                }
                if (permissionWasDenied) Text(stringResource(R.string.onboarding_permission_denied_body), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            3 -> {
                InstructionCard(R.string.onboarding_map_pan)
                InstructionCard(R.string.onboarding_map_zoom)
                InstructionCard(R.string.onboarding_map_tap)
                InstructionCard(R.string.onboarding_map_floors)
            }
            4 -> {
                InstructionCard(R.string.onboarding_modes_gps)
                InstructionCard(R.string.onboarding_modes_manual)
                Text(stringResource(R.string.onboarding_modes_switch), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            5 -> {
                InstructionCard(R.string.onboarding_route_step_1)
                InstructionCard(R.string.onboarding_route_step_2)
                InstructionCard(R.string.onboarding_route_step_3)
                InstructionCard(R.string.onboarding_route_step_4)
            }
            6 -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.onboarding_elevator_warning), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.onboarding_elevator_question), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ElevatorChoice(selected = !useElevator, label = stringResource(R.string.onboarding_elevator_no)) { onUseElevatorChanged(false) }
                ElevatorChoice(selected = useElevator, label = stringResource(R.string.onboarding_elevator_yes)) { onUseElevatorChanged(true) }
            }
            7 -> Text(stringResource(R.string.onboarding_favorites_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            8 -> Text(stringResource(R.string.onboarding_settings_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            else -> {
                Text(stringResource(R.string.onboarding_done_body), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.onboarding_done_signature), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InstructionCard(textId: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(stringResource(textId), modifier = Modifier.fillMaxWidth().padding(13.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ElevatorChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
