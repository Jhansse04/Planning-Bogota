package com.example.planningbgt.util

import com.example.planningbgt.model.Event
import com.example.planningbgt.model.NearbyEvent
import com.example.planningbgt.model.UserLocation

// Calcula qué eventos están cerca del usuario y los ordena por distancia 
object NearbyEventsCalculator {
    const val DEFAULT_RADIUS_METERS = 5_000.0 // 5 km

    // Devuelve los eventos a [radiusMeters] o menos de [origin], del más cercano al más lejano.
    // - Descarta eventos con coordenadas inválidas (NaN o fuera de rango).
    // - Si hay empate de distancia, va primero el que ocurre antes.
    // - Si el origen o el radio no son válidos, devuelve lista vacía (nunca lanza excepción).
    fun calculate(
        events: List<Event>,
        origin: UserLocation,
        radiusMeters: Double = DEFAULT_RADIUS_METERS
    ): List<NearbyEvent> {
        if (!GeoUtils.isValidCoordinate(origin.latitude, origin.longitude)) return emptyList()
        if (!radiusMeters.isFinite() || radiusMeters <= 0.0) return emptyList()

        return events
            .asSequence()
            .filter { GeoUtils.isValidCoordinate(it.location.latitude, it.location.longitude) }
            .map { event ->
                NearbyEvent(
                    event = event,
                    distanceMeters = GeoUtils.distanceMeters(
                        origin.latitude, origin.longitude,
                        event.location.latitude, event.location.longitude
                    )
                )
            }
            .filter { it.distanceMeters <= radiusMeters }
            .sortedWith(
                compareBy<NearbyEvent> { it.distanceMeters }
                    .thenBy { it.event.date.seconds }
            )
            .toList()
    }
}