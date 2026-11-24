package com.example.planningbgt.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.NearbyEvent
import com.example.planningbgt.ui.screens.categoryLabel
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.DateTimeUtils
import com.example.planningbgt.util.GeoUtils

// Hoja inferior "Cerca de mí". No decide nada de negocio: recibe el estado y la lista ya calculada.
// Estados: cargando (skeleton), éxito (lista o vacío), y los problemas (permiso, GPS, timeout, sin servicio).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyEventsSheet(
    state: LocationState,
    hasOrigin: Boolean,
    nearbyEvents: List<NearbyEvent>,
    usingFallbackCenter: Boolean,
    radiusKm: Int,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onUseFallbackCenter: () -> Unit,
    onEventClick: (Event) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isLoading = state is LocationState.Loading
    // Se muestra la lista si hay un origen válido: ubicación real, o el centro de Bogotá elegido por el usuario
    val showList = hasOrigin && (usingFallbackCenter || state is LocationState.Success || isLoading)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nearby_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.location_refresh)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when {
                showList -> NearbyList(
                    items = nearbyEvents,
                    usingFallbackCenter = usingFallbackCenter,
                    radiusKm = radiusKm,
                    isRefreshing = isLoading,
                    onEventClick = onEventClick
                )

                state is LocationState.Idle || isLoading -> NearbySkeleton()

                else -> LocationProblem(
                    state = state,
                    onRetry = onRefresh,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onUseFallbackCenter = onUseFallbackCenter
                )
            }
        }
    }
}

@Composable
private fun NearbyList(
    items: List<NearbyEvent>,
    usingFallbackCenter: Boolean,
    radiusKm: Int,
    isRefreshing: Boolean,
    onEventClick: (Event) -> Unit
) {
    val context = LocalContext.current
    val summary = if (items.isEmpty()) {
        stringResource(R.string.nearby_empty, radiusKm)
    } else {
        context.resources.getQuantityString(
            R.plurals.nearby_results_count, items.size, items.size, radiusKm
        )
    }

    // liveRegion: TalkBack anuncia el resultado cuando cambia (por ejemplo, al actualizar)
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (usingFallbackCenter) {
        Text(
            text = stringResource(R.string.nearby_fallback_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (isRefreshing) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = PrimaryYellow
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        modifier = Modifier.heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.event.eventId }) { item ->
            NearbyEventRow(item = item, onClick = { onEventClick(item.event) })
        }
    }
}

@Composable
private fun NearbyEventRow(item: NearbyEvent, onClick: () -> Unit) {
    val event = item.event
    val distance = GeoUtils.formatDistance(item.distanceMeters)
    val dateText = DateTimeUtils.formatDateTime(event.date.toDate().time)
    val category = categoryLabel(event.category)
    val description = stringResource(R.string.nearby_item_description, event.title, distance, dateText)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.nearby_item_click_label),
                onClick = onClick
            )
            .semantics { contentDescription = description },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$dateText · $category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = distance,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Esqueleto mientras se obtiene la ubicación. clearAndSetSemantics: TalkBack lo lee una sola vez.
@Composable
private fun NearbySkeleton() {
    val loadingText = stringResource(R.string.location_loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = loadingText
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = loadingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}

private data class ProblemUi(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    @param:StringRes val primaryLabel: Int,
    val primaryAction: () -> Unit
)

@Composable
private fun LocationProblem(
    state: LocationState,
    onRetry: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onUseFallbackCenter: () -> Unit
) {
    val ui = when (state) {
        is LocationState.PermissionNeeded -> ProblemUi(
            R.string.location_permission_needed_title,
            R.string.location_permission_needed_message,
            R.string.location_permission_allow,
            onRetry
        )

        is LocationState.PermissionDenied ->
            if (state.canAskAgain) {
                ProblemUi(
                    R.string.location_denied_title,
                    R.string.location_denied_message,
                    R.string.location_retry,
                    onRetry
                )
            } else {
                ProblemUi(
                    R.string.location_denied_title,
                    R.string.location_denied_permanent_message,
                    R.string.location_open_settings,
                    onOpenAppSettings
                )
            }

        is LocationState.GpsDisabled -> ProblemUi(
            R.string.location_gps_off_title,
            R.string.location_gps_off_message,
            R.string.location_open_location_settings,
            onOpenLocationSettings
        )

        is LocationState.Timeout -> ProblemUi(
            R.string.location_timeout_title,
            R.string.location_timeout_message,
            R.string.location_retry,
            onRetry
        )

        else -> ProblemUi(
            R.string.location_unavailable_title,
            R.string.location_unavailable_message,
            R.string.location_retry,
            onRetry
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOff,
            contentDescription = null, // decorativo: el título ya dice lo que pasa
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Título + mensaje como un solo bloque que TalkBack anuncia al aparecer
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(ui.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(ui.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = ui.primaryAction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryYellow,
                contentColor = TextPrimary
            )
        ) {
            Text(stringResource(ui.primaryLabel))
        }
        OutlinedButton(
            onClick = onUseFallbackCenter,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.location_use_bogota_center))
        }
    }
}
