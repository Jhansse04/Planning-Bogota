package com.example.planningbgt.location

import com.example.planningbgt.model.UserLocation

// Caché EN MEMORIA de la última ubicación conocida. Nunca se escribe a disco ni a Firestore.
// Expira solo: pasado el TTL, get() devuelve null.
class LocationCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var entry: Pair<UserLocation, Long>? = null // ubicación + momento en que se guardó

    @Synchronized
    fun get(): UserLocation? {
        val current = entry ?: return null
        return if (clock() - current.second <= ttlMillis) {
            current.first
        } else {
            entry = null
            null
        }
    }

    @Synchronized
    fun put(location: UserLocation) {
        entry = location to clock()
    }

    @Synchronized
    fun clear() {
        entry = null
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 5 * 60_000L // 5 minutos
    }
}