package com.example.planningbgt.util

import com.example.planningbgt.model.Event

/** Motivo por el que alguien NO puede reservar un cupo. */
enum class JoinBlockReason {
    NOT_PUBLIC,             // solo se reserva en eventos públicos
    RESERVATIONS_DISABLED,  // reservationEnabled == false
    REMOVED,                // cancelado o eliminado por su anfitrión
    EXPIRED,                // la fecha ya pasó
    IS_HOST,                // el anfitrión no reserva su propio evento
    ALREADY_JOINED,         // ya está en attendees
    FULL                    // sin cupos
}

/**
 * Reglas de reserva en un solo lugar: las usan la UI (para el estado del botón),
 * la transacción de ReservationRepository y los tests.
 */
object EventJoinRules {

    /** null = puede reservar; si no, el primer motivo que lo impide. */
    fun check(
        event: Event,
        uid: String,
        nowMillis: Long = System.currentTimeMillis()
    ): JoinBlockReason? = when {
        event.eventType != EventFormValidator.TYPE_PUBLIC -> JoinBlockReason.NOT_PUBLIC
        !event.reservationEnabled -> JoinBlockReason.RESERVATIONS_DISABLED
        isRemoved(event) -> JoinBlockReason.REMOVED
        event.date.toDate().time < nowMillis -> JoinBlockReason.EXPIRED
        event.hostId == uid -> JoinBlockReason.IS_HOST
        uid in event.attendees -> JoinBlockReason.ALREADY_JOINED
        isFull(event) -> JoinBlockReason.FULL
        else -> null
    }

    /** Cancelado, o eliminado por su anfitrión (su uid está en hiddenBy). */
    fun isRemoved(event: Event): Boolean =
        event.status == "cancelled" || event.hostId in event.hiddenBy

    /** capacity <= 0 se toma como "sin límite", igual que en ReservationRepository. */
    fun isFull(event: Event): Boolean =
        event.capacity > 0 && event.attendees.size >= event.capacity

    /** Cupos libres, o null si el evento no tiene límite. */
    fun spotsLeft(event: Event): Int? =
        if (event.capacity > 0) event.capacity - event.attendees.size else null
}