package com.example.planningbgt.model

// Ubicación del dispositivo en un instante.
// Solo vive en memoria: nunca se guarda en disco ni en Firestore (privacidad, HU-12).
// toString() omite las coordenadas a propósito, para que no terminen en un log por accidente.
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    override fun toString(): String =
        "UserLocation(accuracyMeters=$accuracyMeters, timestampMillis=$timestampMillis)"
}