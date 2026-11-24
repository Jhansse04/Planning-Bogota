package com.example.planningbgt.model

/**
 * HU-45: cuenta sobre la que se está trabajando. [ownerUid] es quien inició sesión y [uid] la
 * cuenta que se gestiona. La sesión de Firebase nunca cambia: no se guardan credenciales ajenas.
 *
 * Los permisos de aquí solo deciden qué se MUESTRA; quien autoriza es firestore.rules.
 */
data class ActiveAccount(
    val uid: String,
    val ownerUid: String,
    val name: String = "",
    val role: String? = null // null = cuenta propia
) {
    val isDelegated: Boolean get() = uid != ownerUid

    /** Ver el panel, editar nombre y foto, quitar eventos y ampliar cupos. */
    val canManage: Boolean
        get() = !isDelegated || role == AccountLink.ROLE_MANAGE || role == AccountLink.ROLE_ACT

    /** Crear eventos y reservar o cancelar cupos en nombre de la cuenta. */
    val canAct: Boolean get() = !isDelegated || role == AccountLink.ROLE_ACT

    companion object {
        fun own(uid: String) = ActiveAccount(uid = uid, ownerUid = uid)
    }
}
