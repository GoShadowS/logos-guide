package com.logos.navigator.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logos.navigator.R
import com.logos.navigator.data.model.Room
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenDoorsSheet(
    venues: List<Pair<OpenDoorsVenue, Room>>,
    onDismiss: () -> Unit,
    onRoute: (Room) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.88f).dp
    val columnCount = if (configuration.screenWidthDp >= 720) 2 else 1
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedRoomNumber by remember { mutableStateOf<String?>(null) }
    val selectedVenue = venues.firstOrNull { it.first.roomNumber == selectedRoomNumber }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selectedVenue == null) {
                OpenDoorsHeader()
                if (venues.isEmpty()) {
                    Text(
                        stringResource(R.string.open_doors_empty),
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    venues.chunked(columnCount).forEachIndexed { rowIndex, rowVenues ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            rowVenues.forEachIndexed { itemIndex, (venue, room) ->
                                OpenDoorsVenueCard(
                                    index = rowIndex * columnCount + itemIndex + 1,
                                    venue = venue,
                                    room = room,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedRoomNumber = venue.roomNumber },
                                )
                            }
                            if (rowVenues.size < columnCount) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                OpenDoorsVenueDetails(
                    venue = selectedVenue.first,
                    room = selectedVenue.second,
                    onBack = { selectedRoomNumber = null },
                    onRoute = { onRoute(selectedVenue.second) },
                )
            }
        }
    }
}

@Composable
private fun OpenDoorsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                Text(
                    stringResource(R.string.open_doors_today),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                stringResource(R.string.open_doors_heading_main),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
            )
            Text(
                stringResource(R.string.open_doors_heading_accent),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
            )
        }
        Text(
            stringResource(R.string.open_doors_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OpenDoorsVenueCard(
    index: Int,
    venue: OpenDoorsVenue,
    room: Room,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        String.format(Locale.ROOT, "%02d", index),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    venue.title.resolve(context),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    venue.highlights.joinToString(" · ") { it.resolve(context) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    VenueMetadataLine(
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        text = stringResource(R.string.open_doors_room_format, venue.roomNumber, room.floor),
                    )
                    VenueMetadataLine(
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        text = venue.audience.resolve(context),
                    )
                }
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.open_doors_details_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun VenueMetadataLine(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OpenDoorsVenueDetails(
    venue: OpenDoorsVenue,
    room: Room,
    onBack: () -> Unit,
    onRoute: () -> Unit,
) {
    val context = LocalContext.current
    TextButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.open_doors_back))
    }
    Text(
        stringResource(R.string.open_doors_venue_badge),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        venue.title.resolve(context),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    VenueMetadataLine(
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        text = stringResource(R.string.open_doors_room_format, venue.roomNumber, room.floor),
    )
    Text(
        venue.description.resolve(context),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VenueMetadataLine(
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                text = venue.audience.resolve(context),
            )
            Text(
                venue.leader.resolve(context),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                venue.highlights.forEach { highlight ->
                    Text(
                        text = "• ${highlight.resolve(context)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
    Button(
        onClick = onRoute,
        modifier = Modifier.fillMaxWidth(),
        enabled = room.routeAvailable && !room.nodeId.isNullOrBlank(),
    ) {
        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.open_doors_route))
    }
}
