package com.example.planningbgt.util

import com.example.planningbgt.model.AccountLink
import java.util.concurrent.TimeUnit

/** Motivo por el que una operación de cuentas asociadas no se puede hacer. */
enum class AccountLinkError {
    NOT_PRO,             // el plan no incluye la función
    INVALID_EMAIL,
    INVALID_ROLE,
    LABEL_TOO_LONG,
    NAME_REQUIRED,
    WEAK_PASSWORD,
    EMAIL_IN_USE,        // al crear: ya hay una cuenta con ese correo
    ACCOUNT_NOT_FOUND,   // al invitar: nadie usa ese correo
    SELF_LINK,
    ALREADY_LINKED,      // ya existe el vínculo o la invitación
    LIMIT_REACHED,
    INVITATION_EXPIRED,
    LINK_NOT_FOUND,      // ya no existe (o no es de esta cuenta)
    NOT_ALLOWED,         // el servidor lo rechazó
    NETWORK,
    UNKNOWN
}

/**
 * Reglas de cuentas asociadas en un solo lugar: las usan la UI, las transacciones de
 * AccountLinkRepository y los tests. firebase/firestore.rules las repite en el servidor.
 */
object AccountLinkRules {
    const val DEFAULT_MAX_LINKED = 10
    const val UNLIMITED = -1
    const val LABEL_MAX_LENGTH = 40
    const val NAME_MAX_LENGTH = 60
    const val PASSWORD_MIN_LENGTH = 8
    const val INVITATION_TTL_DAYS = 7L

    private val EMAIL_REGEX = Regex("""^[^ @]+@[^ @]+[.][^ @]{2,}$""")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    /** Datos que no dependen del servidor. null = todo bien. */
    fun checkForm(email: String, role: String, label: String): AccountLinkError? = when {
        !isValidEmail(email) -> AccountLinkError.INVALID_EMAIL
        role !in AccountLink.ROLES -> AccountLinkError.INVALID_ROLE
        label.trim().length > LABEL_MAX_LENGTH -> AccountLinkError.LABEL_TOO_LONG
        else -> null
    }

    /** Igual que [checkForm], para crear una cuenta nueva. */
    fun checkNewAccount(
        name: String,
        email: String,
        password: String,
        role: String,
        label: String
    ): AccountLinkError? = when {
        name.isBlank() || name.trim().length > NAME_MAX_LENGTH -> AccountLinkError.NAME_REQUIRED
        password.length < PASSWORD_MIN_LENGTH -> AccountLinkError.WEAK_PASSWORD
        else -> checkForm(email, role, label)
    }

    /** null = [primaryUid] puede asociar a [linkedUid]; si no, el primer motivo que lo impide. */
    fun checkInvite(
        accountType: String?,
        primaryUid: String,
        linkedUid: String,
        currentLinkedUids: List<String>,
        maxLinked: Int
    ): AccountLinkError? = when {
        !PlanPolicy.isAllowed(PlanFeature.ASSOCIATED_ACCOUNTS, accountType) -> AccountLinkError.NOT_PRO
        primaryUid == linkedUid -> AccountLinkError.SELF_LINK
        linkedUid in currentLinkedUids -> AccountLinkError.ALREADY_LINKED
        isLimitReached(currentLinkedUids.size, maxLinked) -> AccountLinkError.LIMIT_REACHED
        else -> null
    }

    /** Las invitaciones pendientes también ocupan cupo: si no, se podría pasar el límite. */
    fun isLimitReached(linkedCount: Int, maxLinked: Int): Boolean =
        maxLinked != UNLIMITED && linkedCount >= maxLinked

    /** Solo vencen las invitaciones sin responder; un vínculo activo no caduca. */
    fun isExpired(link: AccountLink, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val created = link.createdAt?.toDate()?.time ?: return false
        return link.status == AccountLink.STATUS_PENDING &&
            nowMillis >= created + TimeUnit.DAYS.toMillis(INVITATION_TTL_DAYS)
    }

    /** null = [uid] puede aceptar la invitación. */
    fun checkAccept(
        link: AccountLink?,
        uid: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AccountLinkError? = when {
        link == null || link.linkedUid != uid || link.status != AccountLink.STATUS_PENDING ->
            AccountLinkError.LINK_NOT_FOUND
        isExpired(link, nowMillis) -> AccountLinkError.INVITATION_EXPIRED
        else -> null
    }
}
