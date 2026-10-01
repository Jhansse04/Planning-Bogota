package com.example.planningbgt.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class Event(
    val eventId: String = "",
    val title: String = "",
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val location: GeoPoint = GeoPoint(4.6097, -74.0817),
    val capacity: Int = 0,
    val category: String = "",
    val hostId: String = "",
    val eventType: String = "public", // public, private, paid_promotion
    val createdAt: Timestamp = Timestamp.now()
)
