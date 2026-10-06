package com.example.planningbgt.repository

import com.example.planningbgt.model.Event
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class ReservationException(message: String) : Exception(message)

enum class ReserveButtonState { RESERVAR, RESERVADO, EVENTO_LLENO, NO_DISPONIBLE }

class ReservationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val events = db.collection("events")

    // F30-1: cupos disponibles (capacity 0 se toma como "sin límite": confirmar con el equipo)
    fun spotsLeft(event: Event): Int? =
        if (event.capacity > 0) event.capacity - event.attendees.size else null

    // F30-6: estado del botón
    fun buttonState(event: Event, uid: String, cancelled: Boolean = false): ReserveButtonState {
        if (event.attendees.contains(uid)) return ReserveButtonState.RESERVADO
        if (cancelled || event.date.toDate().before(Date())) return ReserveButtonState.NO_DISPONIBLE
        val left = spotsLeft(event)
        if (left != null && left <= 0) return ReserveButtonState.EVENTO_LLENO
        return ReserveButtonState.RESERVAR
    }

    // F30-2, F30-3, F30-4: reservar con transacción (evita sobrecupo)
    suspend fun reserve(eventId: String, uid: String) {
        val ref = events.document(eventId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val event = snap.toObject(Event::class.java)
                ?: throw ReservationException("El evento no existe")
            if ((snap.getString("status") ?: "active") == "cancelled")
                throw ReservationException("El evento fue cancelado")
            if (event.date.toDate().before(Date()))
                throw ReservationException("El evento ya pasó")
            if (event.attendees.contains(uid))
                throw ReservationException("Ya reservaste este evento")
            if (event.capacity > 0 && event.attendees.size >= event.capacity)
                throw ReservationException("Evento lleno")
            tx.update(ref, "attendees", FieldValue.arrayUnion(uid))
            null
        }.await()
    }

    // F30-5: cancelar y liberar el cupo
    suspend fun cancel(eventId: String, uid: String) {
        events.document(eventId)
            .update("attendees", FieldValue.arrayRemove(uid)).await()
    }

    // F30-9 / HU-20: eventos a los que reservó (el historial sale de aquí, sin colección extra)
    suspend fun myReservations(uid: String): List<Event> =
        events.whereArrayContains("attendees", uid).get().await()
            .documents.mapNotNull { it.toObject(Event::class.java)?.copy(eventId = it.id) }
}
