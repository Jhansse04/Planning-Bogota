package com.example.planningbgt.location

import com.example.planningbgt.model.UserLocation

// Resultado de pedir la ubicación. Cada caso se corresponde con un estado de la UI.
sealed class LocationFetchResult {
    // fromCache = true: se devolvió la última ubicación guardada en memoria (sin tocar el sensor)
    data class Success(
        val location: UserLocation,
        val fromCache: Boolean = false
    ) : LocationFetchResult()

    // El usuario no ha concedido (o revocó) el permiso de ubicación
    data object PermissionDenied : LocationFetchResult()

    // Permiso concedido, pero la ubicación del teléfono está apagada
    data object GpsDisabled : LocationFetchResult()

    // No se obtuvo ubicación a tiempo (sin señal, interiores, etc.)
    data object Timeout : LocationFetchResult()

    // Sin Google Play Services o fallo inesperado del sensor
    data object Unavailable : LocationFetchResult()
}

// Contrato que usa la UI. La interfaz permite reemplazarlo por un fake en tests.
interface LocationProvider {
    // forceRefresh = true ignora la caché y pide una ubicación nueva ("actualizar ubicación")
    suspend fun getCurrentLocation(forceRefresh: Boolean = false): LocationFetchResult

    // Borra la ubicación guardada en memoria (por ejemplo, al cerrar sesión)
    fun clearCache()
}