package com.example.planningbgt.ui.screens


import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.repository.JoinEventException
import com.example.planningbgt.repository.ReservationRepository
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.DateTimeUtils
import com.example.planningbgt.util.EventFormValidator
import com.example.planningbgt.util.EventJoinRules
import com.example.planningbgt.util.JoinBlockReason
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale

/**
 * Convierte un VectorDrawable a Bitmap con el tamaño especificado.
 * [tintArgb] opcional: tiñe todo el dibujo (se usa para el pin de eventos públicos).
 */
private fun vectorToBitmap(
    context: android.content.Context,
    drawableId: Int,
    sizePx: Int,
    tintArgb: Int? = null
): Bitmap {
    // mutate(): sin esto, teñir el pin público también teñiría el privado (comparten el dibujo)
    val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate()
        ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    tintArgb?.let { drawable.setTint(it) }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bitmap
}

/**
 * Calcula el tamaño del pin en píxeles según el nivel de zoom.
 * Zoom 10 o menos → pin pequeño (48px), Zoom 18+ → pin grande (120px).
 */
private fun pinSizeForZoom(zoom: Double): Int {
    val minZoom = 10.0
    val maxZoom = 18.0
    val minSize = 48
    val maxSize = 120
    val clampedZoom = zoom.coerceIn(minZoom, maxZoom)
    val fraction = (clampedZoom - minZoom) / (maxZoom - minZoom)
    return (minSize + fraction * (maxSize - minSize)).toInt()
}

/** Agrupa el tamaño en escalones de 12 px: así se redibuja pocas veces al hacer zoom. */
private fun pinSizeStep(zoom: Double): Int = (pinSizeForZoom(zoom) / 12) * 12

/** Dibujo y color de un pin. */
private data class PinStyle(val drawableId: Int, val tintArgb: Int?)

/**
 * Único punto para diferenciar los pines por tipo de evento:
 * privado = pin actual (amarillo, sin tocar), público = el mismo pin teñido de negro.
 */
private fun pinStyleFor(event: Event): PinStyle =
    if (event.eventType == EventFormValidator.TYPE_PUBLIC) {
        PinStyle(R.drawable.pin_event, TextPrimary.toArgb())
    } else {
        PinStyle(R.drawable.pin_event, null)
    }

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firestoreRepository = remember { FirestoreRepository() }
    val events = remember { mutableStateListOf<Event>() }
    var eventsVersion by remember { mutableIntStateOf(0) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var pinSizePx by remember { mutableIntStateOf(pinSizeStep(13.0)) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    // marker.id -> evento: así sabemos a qué evento pertenece el pin tocado
    val markerEvents = remember { HashMap<Long, Event>() }

    // Inicializar MapLibre
    MapLibre.getInstance(context)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // Estilo, cámara y listeners: UNA sola vez
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { _ ->
                // Centrar en Bogotá
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(4.6097, -74.0817))
                    .zoom(13.0)
                    .build()

                map.setOnMarkerClickListener { marker ->
                    markerEvents[marker.id]?.let { selectedEvent = it }
                    true // consume el toque: sin ventana de información por defecto
                }

                // Solo cambia el estado cuando el tamaño del pin pasa a otro escalón
                map.addOnCameraMoveListener {
                    val step = pinSizeStep(map.cameraPosition.zoom)
                    if (step != pinSizePx) pinSizePx = step
                }

                mapLibreMap = map
            }
        }
    }

    // Cargar eventos al entrar: públicos + privados visibles (propios y de cuentas enlazadas)
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val (publicEvents, privateEvents) = coroutineScope {
            val pub = async { firestoreRepository.getPublicEvents().getOrDefault(emptyList()) }
            val priv = async {
                if (uid != null) {
                    firestoreRepository.getPrivateEventsForUser(uid).getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            pub.await() to priv.await()
        }
        events.clear()
        events.addAll((publicEvents + privateEvents).distinctBy { it.eventId })
        eventsVersion++
    }

    // Dibujar pines: al cargar eventos, al terminar de cargar el mapa o al cambiar de escalón
    LaunchedEffect(mapLibreMap, pinSizePx, eventsVersion) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.annotations?.let { map.removeAnnotations(it) }
        markerEvents.clear()

        val icons = HashMap<PinStyle, Icon>()
        for (event in events) {
            val style = pinStyleFor(event)
            val icon = icons.getOrPut(style) {
                IconFactory.getInstance(context).fromBitmap(
                    vectorToBitmap(context, style.drawableId, pinSizePx, style.tintArgb)
                )
            }
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(event.location.latitude, event.location.longitude))
                    .icon(icon)
            )
            markerEvents[marker.id] = event
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Diálogo del evento tocado. Al reservar/cancelar se actualiza el evento en memoria
    // (lista y pines) para que, si se vuelve a abrir, muestre el estado real.
    selectedEvent?.let { ev ->
        EventInfoDialog(
            event = ev,
            onDismiss = { selectedEvent = null },
            onEventUpdated = { updated ->
                val index = events.indexOfFirst { it.eventId == updated.eventId }
                if (index >= 0) events[index] = updated
                markerEvents.replaceAll { _, e -> if (e.eventId == updated.eventId) updated else e }
                selectedEvent = updated
            },
            onEventDeleted = { id ->
                events.removeAll { it.eventId == id }
                eventsVersion++ // redibuja los pines sin ese evento
                selectedEvent = null
            }
        )
    }
}

/** Texto (strings.xml) para cada motivo por el que no se puede reservar. */
private fun joinReasonRes(reason: JoinBlockReason): Int = when (reason) {
    JoinBlockReason.NOT_PUBLIC -> R.string.event_detail_not_public
    JoinBlockReason.RESERVATIONS_DISABLED -> R.string.event_detail_no_reservations
    JoinBlockReason.REMOVED, JoinBlockReason.EXPIRED -> R.string.event_detail_unavailable
    JoinBlockReason.IS_HOST -> R.string.event_detail_is_host
    JoinBlockReason.ALREADY_JOINED -> R.string.event_detail_already_joined
    JoinBlockReason.FULL -> R.string.event_detail_full
}


@Composable
private fun EventInfoDialog(
    event: Event,
    onDismiss: () -> Unit,
    onEventUpdated: (Event) -> Unit,
    onEventDeleted: (String) -> Unit
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val reservationRepo = remember { ReservationRepository() }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var isWorking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showIncreaseDialog by remember { mutableStateOf(false) }
    var photoExpanded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf<User?>(null) }

    val isPublic = event.eventType == EventFormValidator.TYPE_PUBLIC
    val isHost = uid != null && uid == event.hostId
    val hasPhoto = event.imageUrl.isNotBlank()
    val hasReservation = uid != null && uid in event.attendees
    val blockReason = EventJoinRules.check(event, uid.orEmpty())
    val spotsLeft = EventJoinRules.spotsLeft(event)
    val genericError = stringResource(R.string.event_detail_error_generic)

    // Nombre y foto del anfitrión (solo eso: nunca el correo ni otros datos)
    LaunchedEffect(event.hostId) {
        host = firestoreRepo.getUser(event.hostId)
    }

    // reserve = true reserva; false cancela. La validación real vive en ReservationRepository.
    fun reserveOrCancel(reserve: Boolean) {
        if (uid == null || isWorking) return
        isWorking = true
        errorText = null
        scope.launch {
            try {
                if (reserve) {
                    reservationRepo.reserve(event.eventId, uid)
                    onEventUpdated(event.copy(attendees = event.attendees + uid))
                } else {
                    reservationRepo.cancel(event.eventId, uid)
                    onEventUpdated(event.copy(attendees = event.attendees - uid))
                }
            } catch (e: Exception) {
                errorText = when (e) {
                    is JoinEventException -> context.getString(joinReasonRes(e.reason))
                    else -> genericError
                }
                // Mostrar el estado real (por ejemplo, si alguien tomó el último cupo)
                firestoreRepo.getEventById(event.eventId).getOrNull()?.let(onEventUpdated)
            } finally {
                isWorking = false
            }
        }
    }

    // Evento lleno: el usuario avisa al anfitrión para que amplíe la capacidad
    fun askHostForSpots() {
        if (uid == null || isWorking) return
        isWorking = true
        errorText = null
        scope.launch {
            firestoreRepo.requestMoreCapacity(event.eventId, uid).fold(
                onSuccess = {
                    onEventUpdated(event.copy(capacityRequests = event.capacityRequests + uid))
                },
                onFailure = { errorText = genericError }
            )
            isWorking = false
        }
    }

    // Anfitrión: amplía la capacidad (la validación real vive en FirestoreRepository)
    fun increaseCapacity(newCapacity: Int) {
        if (uid == null || isWorking) return
        isWorking = true
        errorText = null
        scope.launch {
            firestoreRepo.increaseCapacity(event.eventId, uid, newCapacity).fold(
                onSuccess = {
                    onEventUpdated(event.copy(capacity = newCapacity, capacityRequests = emptyList()))
                },
                onFailure = { errorText = genericError }
            )
            isWorking = false
        }
    }

    // Anfitrión: elimina para todos (su uid queda en hiddenBy => el evento se considera eliminado)
    fun deleteEvent() {
        if (uid == null || isWorking) return
        isWorking = true
        errorText = null
        scope.launch {
            firestoreRepo.hideEventFromPanel(event.eventId, uid).fold(
                onSuccess = { onEventDeleted(event.eventId) },
                onFailure = { errorText = genericError }
            )
            isWorking = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = event.title, modifier = Modifier.weight(1f))
                // Miniatura arriba a la derecha (solo si el anfitrión puso foto)
                if (hasPhoto && !photoExpanded) {
                    EventPhoto(
                        imageUrl = event.imageUrl,
                        clickLabel = stringResource(R.string.event_detail_photo_expand),
                        onClick = { photoExpanded = true },
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasPhoto && photoExpanded) {
                    EventPhoto(
                        imageUrl = event.imageUrl,
                        clickLabel = stringResource(R.string.event_detail_photo_collapse),
                        onClick = { photoExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }

                Text(
                    text = stringResource(
                        if (isPublic) R.string.create_event_type_public
                        else R.string.create_event_type_private
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                // Anfitrión: nombre y foto de perfil
                host?.let { h ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HostAvatar(h)
                        Column {
                            Text(
                                text = stringResource(R.string.event_detail_host_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = h.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = DateTimeUtils.formatDateTime(event.date.toDate().time),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (event.category.isNotBlank()) {
                    Text(
                        text = categoryLabel(event.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Ubicación: coordenadas + abrir en Google Maps (sin apikey)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.5f, %.5f",
                            event.location.latitude,
                            event.location.longitude
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            openInGoogleMaps(
                                context,
                                event.location.latitude,
                                event.location.longitude
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.event_detail_open_maps))
                    }
                }

                if (isPublic && event.reservationEnabled && spotsLeft != null) {
                    Text(
                        text = stringResource(R.string.event_detail_spots, spotsLeft, event.capacity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Zona de acción
                when {
                    hasReservation -> OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.event_detail_cancel_reservation))
                    }

                    blockReason == null -> Button(
                        onClick = { reserveOrCancel(true) },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryYellow,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.event_detail_reserve))
                    }

                    blockReason == JoinBlockReason.FULL -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.event_detail_full))
                        }
                        if (uid != null && uid in event.capacityRequests) {
                            Text(
                                text = stringResource(R.string.event_detail_notified),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedButton(
                                onClick = { askHostForSpots() },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.event_detail_notify_host))
                            }
                        }
                    }

                    // Privados: solo lectura, sin botones ni mensaje
                    blockReason == JoinBlockReason.NOT_PUBLIC -> {}

                    else -> Text(
                        text = stringResource(joinReasonRes(blockReason)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Acciones del anfitrión
                if (isHost && isPublic && event.reservationEnabled) {
                    if (event.capacityRequests.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.event_detail_requests,
                                event.capacityRequests.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (event.capacity < EventFormValidator.CAPACITY_MAX_PUBLIC) {
                        OutlinedButton(
                            onClick = { showIncreaseDialog = true },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.event_detail_increase))
                        }
                    }
                }
                if (isHost) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.event_detail_delete))
                    }
                }

                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(stringResource(R.string.event_detail_close))
            }
        }
    )

    // Confirmación antes de liberar el cupo
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.event_detail_cancel_title)) },
            text = { Text(stringResource(R.string.event_detail_cancel_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        reserveOrCancel(false)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.event_detail_cancel_reservation),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirm = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.event_detail_keep_reservation))
                }
            }
        )
    }

    // Confirmación antes de eliminar el evento (para todos)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.event_detail_delete_title)) },
            text = { Text(stringResource(R.string.event_detail_delete_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteEvent()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.event_detail_delete_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.event_detail_delete_keep))
                }
            }
        )
    }

    // Anfitrión: ampliar la capacidad (solo hacia arriba y hasta el máximo de públicos)
    if (showIncreaseDialog) {
        var newCapacityText by remember { mutableStateOf("") }
        val newCapacity = newCapacityText.toIntOrNull()
        val isValid = newCapacity != null &&
            newCapacity > event.capacity &&
            newCapacity <= EventFormValidator.CAPACITY_MAX_PUBLIC

        AlertDialog(
            onDismissRequest = { showIncreaseDialog = false },
            title = { Text(stringResource(R.string.event_detail_increase)) },
            text = {
                OutlinedTextField(
                    value = newCapacityText,
                    onValueChange = { newCapacityText = it.filter(Char::isDigit).take(3) },
                    label = {
                        Text(stringResource(R.string.event_detail_increase_label, event.capacity))
                    },
                    isError = newCapacityText.isNotEmpty() && !isValid,
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.event_detail_increase_hint,
                                event.capacity + 1,
                                EventFormValidator.CAPACITY_MAX_PUBLIC
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showIncreaseDialog = false
                        newCapacity?.let { increaseCapacity(it) }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.event_detail_increase),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showIncreaseDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
@Composable
private fun EventPhoto(
    imageUrl: String,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Si la URL falla o tarda, se ve un recuadro neutro en vez de un hueco vacío
    val neutral = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    AsyncImage(
        model = imageUrl,
        contentDescription = stringResource(R.string.event_detail_photo),
        placeholder = neutral,
        error = neutral,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    )
}

/** Avatar del anfitrión: su foto de perfil, o su inicial sobre fondo amarillo. */
@Composable
private fun HostAvatar(user: User) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(PrimaryYellow),
        contentAlignment = Alignment.Center
    ) {
        if (user.profilePictureUrl.isNotBlank()) {
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = null, // decorativo: el nombre está al lado
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = user.name.firstOrNull()?.uppercase() ?: "U",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

/** Abre la ubicación en Google Maps (o en el navegador si no está instalado) */
private fun openInGoogleMaps(context: Context, lat: Double, lng: Double) {
    val query = String.format(Locale.US, "%.6f,%.6f", lat, lng)
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin navegador ni Maps: no hay nada que abrir
    }
}
