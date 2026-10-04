package com.example.planningbgt.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.repository.FirestoreRepository
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Convierte un VectorDrawable a Bitmap con el tamaño especificado.
 */
private fun vectorToBitmap(context: android.content.Context, drawableId: Int, sizePx: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId)
        ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
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

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firestoreRepository = remember { FirestoreRepository() }
    val events = remember { mutableStateListOf<Event>() }

    // Inicializar MapLibre
    MapLibre.getInstance(context)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // Cargar eventos de Firestore
    LaunchedEffect(Unit) {
        val loaded = firestoreRepository.getEvents()
        events.clear()
        events.addAll(loaded)
    }

    // Cuando los eventos se carguen, agregar marcadores al mapa
    LaunchedEffect(events.size) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { _ ->
                // Limpiar marcadores previos
                map.annotations?.let { map.removeAnnotations(it) }

                // Función para agregar marcadores con el tamaño actual
                fun addMarkers(zoomLevel: Double) {
                    map.annotations?.let { map.removeAnnotations(it) }
                    val sizePx = pinSizeForZoom(zoomLevel)
                    val bitmap = vectorToBitmap(context, R.drawable.pin_event, sizePx)
                    val icon: Icon = IconFactory.getInstance(context).fromBitmap(bitmap)

                    for (event in events) {
                        val position = LatLng(event.location.latitude, event.location.longitude)
                        map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(event.title)
                                .snippet(event.description)
                                .icon(icon)
                        )
                    }
                }

                // Centrar en Bogotá
                val bogota = LatLng(4.6097, -74.0817)
                map.cameraPosition = CameraPosition.Builder()
                    .target(bogota)
                    .zoom(13.0)
                    .build()

                // Agregar marcadores iniciales
                addMarkers(13.0)

                // Escalar pines cuando cambia el zoom
                map.addOnCameraMoveListener {
                    addMarkers(map.cameraPosition.zoom)
                }
            }
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
}
