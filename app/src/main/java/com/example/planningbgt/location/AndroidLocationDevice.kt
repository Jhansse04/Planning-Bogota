package com.example.planningbgt.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.planningbgt.model.UserLocation
import com.example.planningbgt.util.GeoUtils
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

// Implementación real de LocationDevice con FusedLocationProviderClient.
// El permiso se verifica en LocationService antes de llamar a fetchCurrent/fetchLast.
class AndroidLocationDevice(
    context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
) : LocationDevice {

    // applicationContext: no retiene la Activity (evita fugas de memoria)
    private val appContext = context.applicationContext

    override fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse // con solo "aproximada" también sirve para eventos cercanos
    }

    override fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    override fun isPlayServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission")
    override suspend fun fetchCurrent(): UserLocation? {
        val tokenSource = CancellationTokenSource()
        return try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .await()
                ?.toUserLocation()
        } catch (e: CancellationException) {
            // Si la corrutina se cancela (timeout o el usuario sale), se cancela también la petición al sensor
            tokenSource.cancel()
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun fetchLast(): UserLocation? =
        client.lastLocation.await()?.toUserLocation()

    // Descarta coordenadas inválidas (NaN, fuera de rango)
    private fun Location.toUserLocation(): UserLocation? =
        if (GeoUtils.isValidCoordinate(latitude, longitude)) {
            UserLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = if (hasAccuracy()) accuracy else null,
                timestampMillis = time
            )
        } else {
            null
        }
}