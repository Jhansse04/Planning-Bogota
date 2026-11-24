package com.example.planningbgt.model

import com.google.firebase.Timestamp

/**
 * HU-45: vínculo entre la cuenta principal (Pro) y una cuenta asociada.
 * Vive en accountLinks/{primaryUid}_{linkedUid}: el id hace imposible el duplicado.
 * Un vínculo rechazado, cancelado o desasociado se borra; su historia queda en accountAudit.
 */
data class AccountLink(
    val linkId: String = "",
    val primaryUid: String = "",
    val linkedUid: String = "",
    val linkedEmail: String = "",
    val role: String = ROLE_MANAGE, // manage, act
    val label: String = "",
    val status: String = STATUS_PENDING, // pending, active
    // null mientras el servidor todavía no confirma la hora (serverTimestamp)
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val respondedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACTIVE = "active"

        /** Solo gestionar: ver, editar nombre/foto y administrar eventos que ya existen. */
        const val ROLE_MANAGE = "manage"

        /** Actuar en su nombre: lo anterior + crear eventos y reservar como esa cuenta. */
        const val ROLE_ACT = "act"

        val ROLES = listOf(ROLE_MANAGE, ROLE_ACT)

        fun idFor(primaryUid: String, linkedUid: String) = "${primaryUid}_$linkedUid"
    }
}
