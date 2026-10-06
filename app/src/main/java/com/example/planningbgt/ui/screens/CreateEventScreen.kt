package com.example.planningbgt.ui.screens

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.DateTimeUtils
import com.example.planningbgt.util.EventFormValidator
import com.example.planningbgt.util.FieldError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

private const val MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val BOGOTA_LAT = 4.6097
private const val BOGOTA_LNG = -74.0817

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onBack: () -> Unit,
    onEventCreated: () -> Unit
) {
    val repo = remember { FirestoreRepository() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    // rememberSaveable: no se pierde lo escrito al rotar la pantalla
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var capacityText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var dateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var latitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var longitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateUtcMillis by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    val errors = EventFormValidator.validate(
        title = title,
        description = description,
        dateMillis = dateMillis,
        hasLocation = latitude != null && longitude != null,
        capacityText = capacityText,
        category = category
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.create_event_title),
                onBack = onBack
            )

            // Título
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.create_event_field_title)) },
                isError = showErrors && errors.title != null,
                supportingText = {
                    val e = errors.title
                    if (showErrors && e != null) {
                        Text(fieldErrorText(e, EventFormValidator.TITLE_MAX_LENGTH))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Descripción
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.create_event_field_description)) },
                isError = showErrors && errors.description != null,
                supportingText = {
                    val e = errors.description
                    if (showErrors && e != null) {
                        Text(fieldErrorText(e, EventFormValidator.DESCRIPTION_MAX_LENGTH))
                    }
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Fecha y hora
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    dateMillis?.let { DateTimeUtils.formatDateTime(it) }
                        ?: stringResource(R.string.create_event_select_date)
                )
            }
            errors.date?.let { e ->
                if (showErrors) ErrorLine(fieldErrorText(e))
            }

            // Capacidad
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.create_event_field_capacity)) },
                isError = showErrors && errors.capacity != null,
                supportingText = {
                    val e = errors.capacity
                    if (showErrors && e != null) Text(fieldErrorText(e))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Categoría
            Text(
                text = stringResource(R.string.create_event_category),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventFormValidator.categories.forEach { key ->
                    FilterChip(
                        selected = category == key,
                        onClick = { category = key },
                        label = { Text(categoryLabel(key)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryYellow,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }
            if (showErrors && errors.category != null) {
                ErrorLine(stringResource(R.string.error_category_required))
            }

            // Ubicación
            Text(
                text = stringResource(R.string.create_event_location),
                style = MaterialTheme.typography.titleSmall
            )
            LocationPickerMap(
                selectedLat = latitude,
                selectedLng = longitude,
                onPick = { lat, lng ->
                    latitude = lat
                    longitude = lng
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
            val lat = latitude
            val lng = longitude
            Text(
                text = if (lat != null && lng != null) {
                    stringResource(R.string.create_event_location_selected, lat, lng)
                } else {
                    stringResource(R.string.create_event_location_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showErrors && errors.location != null) {
                ErrorLine(stringResource(R.string.error_location_required))
            }

            if (saveFailed) {
                ErrorLine(stringResource(R.string.create_event_save_error))
            }

            Button(
                onClick = {
                    showErrors = true
                    saveFailed = false
                    val millis = dateMillis
                    val eventLat = latitude
                    val eventLng = longitude
                    val eventCategory = category
                    if (!errors.hasErrors && !isSaving && uid != null &&
                        millis != null && eventLat != null && eventLng != null &&
                        eventCategory != null
                    ) {
                        isSaving = true
                        val event = Event(
                            title = title.trim(),
                            description = description.trim(),
                            date = Timestamp(Date(millis)),
                            location = GeoPoint(eventLat, eventLng),
                            capacity = capacityText.trim().toInt(),
                            category = eventCategory,
                            hostId = uid
                        )
                        scope.launch {
                            repo.createPrivateEvent(event, uid).fold(
                                onSuccess = { onEventCreated() },
                                onFailure = {
                                    saveFailed = true
                                    isSaving = false
                                }
                            )
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryYellow,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    stringResource(
                        if (isSaving) R.string.create_event_saving else R.string.create_event_save
                    )
                )
            }
        }
    }

    // ── Diálogo de fecha (no permite días pasados) ──
    if (showDatePicker) {
        val todayUtcMillis = remember {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= todayUtcMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateUtcMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Diálogo de hora ──
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = 18,
            initialMinute = 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.create_event_select_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateUtcMillis?.let { utc ->
                            dateMillis = DateTimeUtils.combineDateAndTime(
                                utcDateMillis = utc,
                                hour = timePickerState.hour,
                                minute = timePickerState.minute
                            )
                        }
                        showTimePicker = false
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** Mini mapa MapLibre: un toque coloca (o mueve) el pin de ubicación. */
@Composable
private fun LocationPickerMap(
    selectedLat: Double?,
    selectedLng: Double?,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    MapLibre.getInstance(context)

    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    val currentOnPick by rememberUpdatedState(onPick)
    val markerHolder = remember { arrayOfNulls<Marker>(1) }

    // Estilo, cámara inicial en Bogotá y listener de toques
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URI)) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(BOGOTA_LAT, BOGOTA_LNG))
                    .zoom(11.0)
                    .build()
            }
            map.addOnMapClickListener { point ->
                currentOnPick(point.latitude, point.longitude)
                true
            }
        }
    }

    // Dibuja/mueve el pin cuando cambia la ubicación elegida
    LaunchedEffect(selectedLat, selectedLng) {
        if (selectedLat == null || selectedLng == null) return@LaunchedEffect
        mapView.getMapAsync { map ->
            markerHolder[0]?.let { map.removeMarker(it) }
            markerHolder[0] = map.addMarker(
                MarkerOptions().position(LatLng(selectedLat, selectedLng))
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Cierra solo lo que el observer dejó abierto, y destruye una única vez.
            val state = lifecycleOwner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                // El mapa vive dentro de un Column con scroll: sin esto, arrastrar el mapa
                // haría scroll de la pantalla en vez de mover el mapa.
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun fieldErrorText(error: FieldError, maxLength: Int = 0): String = when (error) {
    FieldError.REQUIRED -> stringResource(R.string.error_required)
    FieldError.TOO_LONG -> stringResource(R.string.error_too_long, maxLength)
    FieldError.INVALID_NUMBER -> stringResource(R.string.error_invalid_number)
    FieldError.OUT_OF_RANGE -> stringResource(
        R.string.error_capacity_range,
        EventFormValidator.CAPACITY_MIN,
        EventFormValidator.CAPACITY_MAX
    )
    FieldError.DATE_IN_PAST -> stringResource(R.string.error_date_past)
}

/** Etiqueta visible de una categoría guardada en Firestore (también la usa el panel). */
@Composable
internal fun categoryLabel(key: String): String = when (key) {
    "fiesta" -> stringResource(R.string.category_fiesta)
    "deporte" -> stringResource(R.string.category_deporte)
    "cultural" -> stringResource(R.string.category_cultural)
    "academico" -> stringResource(R.string.category_academico)
    "gastronomia" -> stringResource(R.string.category_gastronomia)
    "musica" -> stringResource(R.string.category_musica)
    "otro" -> stringResource(R.string.category_otro)
    "" -> stringResource(R.string.category_none)
    else -> key
}