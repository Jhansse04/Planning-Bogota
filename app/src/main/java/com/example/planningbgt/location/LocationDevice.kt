package com.example.planningbgt.location

import com.example.planningbgt.model.UserLocation

// Capa delgada sobre Android/Google Play Services. Existe SOLO para poder testear
// LocationService con JUnit + MockK, sin emulador y sin tocar Context.
interface LocationDevice {
    fun hasLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    fun isPlayServicesAvailable(): Boolean

    // Pide una ubicación nueva. Devuelve null si el sensor no logró un punto.
    suspend fun fetchCurrent(): UserLocation?

    // Última ubicación que conoce el sistema (puede ser vieja). null si no hay ninguna.
    suspend fun fetchLast(): UserLocation?
}