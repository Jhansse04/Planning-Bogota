package com.example.planningbgt.model

/** Un evento junto con su distancia (en metros) al usuario. */
data class NearbyEvent(
    val event: Event,
    val distanceMeters: Double
)