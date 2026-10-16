package com.logos.navigator.ui.map

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.logos.navigator.R
import com.logos.navigator.data.model.GeoCoordinate
import kotlinx.coroutines.CancellationException
import ru.dgis.sdk.Context as DgisContext
import ru.dgis.sdk.map.CameraPosition
import ru.dgis.sdk.map.DefaultMapControllerViewModel
import ru.dgis.sdk.map.GeoPoint
import ru.dgis.sdk.map.Map
import ru.dgis.sdk.map.MapControllerOptions
import ru.dgis.sdk.map.MapView
import ru.dgis.sdk.map.MyLocationMapObjectSource
import ru.dgis.sdk.map.Zoom

/** Наружная карта 2ГИС; внутренний план и маршруты остаются на Kotlin Canvas. */
@Composable
fun DgisOutdoorMap(
    sdkContext: DgisContext?,
    initialCoordinate: GeoCoordinate?,
    currentCoordinate: GeoCoordinate?,
    recenterRequest: Int,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    if (sdkContext == null) {
        DgisKeyRequiredCard(modifier = modifier)
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val mapControllerViewModel = remember(sdkContext, initialCoordinate) {
        DefaultMapControllerViewModel(
            sdkContext,
            MapControllerOptions(
                position = initialCoordinate?.toCameraPosition(),
            ),
        )
    }
    val locationSource = remember(sdkContext) { MyLocationMapObjectSource(sdkContext) }
    val mapReference = remember(mapControllerViewModel) { mutableStateOf<Map?>(null) }
    var mapView by remember(mapControllerViewModel) { mutableStateOf<MapView?>(null) }
    var mapCreationFailed by remember(mapControllerViewModel) { mutableStateOf(false) }
    var locationSourceAdded by remember(mapControllerViewModel) { mutableStateOf(false) }
    var hasCenteredToCurrentLocation by remember(mapControllerViewModel) { mutableStateOf(false) }
    var handledRecenterRequest by remember(mapControllerViewModel) { mutableStateOf(recenterRequest) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                MapView(viewContext).apply {
                    setMapControllerViewModel(mapControllerViewModel)
                }.also { mapView = it }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> mapView = view },
        )

        if (mapCreationFailed) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = stringResource(R.string.dgis_map_load_error),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    ObserveMapViewLifecycle(lifecycleOwner, mapView)

    DisposableEffect(mapControllerViewModel, locationSource) {
        onDispose {
            mapReference.value?.let { map -> runCatching { map.removeSource(locationSource) } }
            runCatching { locationSource.close() }
            mapControllerViewModel.close()
        }
    }

    LaunchedEffect(mapControllerViewModel, locationSource, hasLocationPermission) {
        try {
            val map = mapControllerViewModel.awaitMap()
            mapReference.value = map
            if (hasLocationPermission && !locationSourceAdded) {
                map.addSource(locationSource)
                locationSourceAdded = true
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e("LogosNavigator", "Не удалось создать карту 2ГИС", exception)
            mapCreationFailed = true
        }
    }

    LaunchedEffect(mapReference.value, currentCoordinate, recenterRequest) {
        val map = mapReference.value ?: return@LaunchedEffect
        val coordinate = currentCoordinate ?: return@LaunchedEffect
        if (!hasCenteredToCurrentLocation || recenterRequest != handledRecenterRequest) {
            map.camera.move(coordinate.toCameraPosition())
            hasCenteredToCurrentLocation = true
            handledRecenterRequest = recenterRequest
        }
    }
}

@Composable
private fun ObserveMapViewLifecycle(lifecycleOwner: LifecycleOwner, mapView: MapView?) {
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView
        if (view != null) lifecycleOwner.lifecycle.addObserver(view)
        onDispose {
            if (view != null) lifecycleOwner.lifecycle.removeObserver(view)
        }
    }
}

@Composable
private fun DgisKeyRequiredCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.dgis_key_required_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.dgis_key_required_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun GeoCoordinate.toCameraPosition(): CameraPosition = CameraPosition(
    point = GeoPoint(latitude, longitude),
    zoom = Zoom(16.0f),
)
