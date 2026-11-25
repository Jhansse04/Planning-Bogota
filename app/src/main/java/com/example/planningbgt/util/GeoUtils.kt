package com.example.planningbgt.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        // coerceIn: por redondeo de punto flotante
        val safeA = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(safeA), sqrt(1 - safeA))
        return EARTH_RADIUS_METERS * c
    }

    /** true si la latitud está en [-90, 90] y la longitud en [-180, 180] (y ninguna es NaN/infinita). */
    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    /**
     
     * - menos de 1 km: metros redondeados a la decena ("350 m")
     * - de 1 km a 10 km: un decimal ("2,4 km")
     * - 10 km o más: sin decimales ("12 km")
     *
     * Devuelve "" si la distancia no es un número válido.
     * [locale] es parámetro para poder testear el separador decimal.
     */
    fun formatDistance(distanceMeters: Double, locale: Locale = Locale.getDefault()): String {
        if (!distanceMeters.isFinite()) return ""
        val meters = distanceMeters.coerceAtLeast(0.0)
        val roundedMeters = (meters / 10).roundToInt() * 10
        return when {
            roundedMeters < 1000 -> String.format(locale, "%d m", roundedMeters)
            meters < 10_000 -> String.format(locale, "%.1f km", meters / 1000)
            else -> String.format(locale, "%.0f km", meters / 1000)
        }
    }
}