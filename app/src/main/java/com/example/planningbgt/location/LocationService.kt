package com.example.planningbgt.location

import android.content.Context
import com.example.planningbgt.model.UserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

// Lógica de la HU-12 para obtener la ubicación. No toca Android directamente:
// todo pasa por LocationDevice, por eso se puede testear con JUnit + MockK.
//
// Orden de decisión:
//   1. ¿Hay permiso?                  no -> PermissionDenied (y se borra la caché)
//   2. ¿Hay caché vigente?            sí -> Success(fromCache = true)   [salvo forceRefresh]
//   3. ¿Hay Play Services?            no -> Unavailable
//   4. ¿Está encendida la ubicación?  no -> GpsDisabled
//   5. Pedir ubicación con timeout    ok -> Success; si falla, usa la última conocida reciente; si no -> Timeout
//
// Privacidad: no escribe logs (así no hay riesgo de que salgan coordenadas).
class LocationService(
    private val device: LocationDevice,
    private val cache: LocationCache = sharedCache,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val lastKnownMaxAgeMillis: Long = LAST_KNOWN_MAX_AGE_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) : LocationProvider {

    override suspend fun getCurrentLocation(forceRefresh: Boolean): LocationFetchResult {
        if (!device.hasLocationPermission()) {
            cache.clear() // sin permiso no se conserva ni se sirve ninguna ubicación
            return LocationFetchResult.PermissionDenied
        }

        if (!forceRefresh) {
            cache.get()?.let { return LocationFetchResult.Success(it, fromCache = true) }
        }

        if (!device.isPlayServicesAvailable()) return LocationFetchResult.Unavailable
        if (!device.isLocationEnabled()) return LocationFetchResult.GpsDisabled

        return try {
            val fresh = withTimeout(timeoutMillis) { device.fetchCurrent() }
            val location = fresh ?: recentLastKnown()
            succeedOrTimeout(location)
        } catch (e: TimeoutCancellationException) {
            // Debe ir antes que CancellationException: es una subclase suya
            succeedOrTimeout(recentLastKnown())
        } catch (e: CancellationException) {
            throw e // cancelación normal de la corrutina: nunca se traga
        } catch (e: SecurityException) {
            cache.clear() // el permiso se revocó mientras se pedía la ubicación
            LocationFetchResult.PermissionDenied
        } catch (e: Exception) {
            LocationFetchResult.Unavailable
        }
    }

    override fun clearCache() = cache.clear()

    private fun succeedOrTimeout(location: UserLocation?): LocationFetchResult =
        if (location != null) {
            cache.put(location)
            LocationFetchResult.Success(location)
        } else {
            LocationFetchResult.Timeout
        }

    // Última ubicación del sistema, solo si es reciente (una de hace horas daría eventos "cercanos" falsos)
    private suspend fun recentLastKnown(): UserLocation? = try {
        device.fetchLast()?.takeIf { clock() - it.timestampMillis <= lastKnownMaxAgeMillis }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val LAST_KNOWN_MAX_AGE_MILLIS = 10 * 60_000L

        // Caché compartida: sobrevive al cambiar de pestaña (MapScreen sale de la composición)
        private val sharedCache = LocationCache()

        fun create(context: Context): LocationService =
            LocationService(AndroidLocationDevice(context))

        // Para llamarla al cerrar sesión, sin necesitar una instancia
        fun clearSharedCache() = sharedCache.clear()
    }
}