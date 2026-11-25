package com.example.planningbgt.repository

import android.util.Log
import com.example.planningbgt.model.AccountLink
import com.example.planningbgt.model.ActiveAccount
import com.example.planningbgt.util.AccountLinkError
import com.example.planningbgt.util.AccountLinkRules
import com.example.planningbgt.util.PlanFeature
import com.example.planningbgt.util.PlanPolicy
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** No se pudo hacer una operación de cuentas asociadas; la UI traduce [reason] a su texto. */
class AccountLinkException(val reason: AccountLinkError) :
    Exception("Cuentas asociadas: $reason")

/**
 * HU-45: vínculos entre cuentas. Las validaciones de aquí dan mensajes claros; las que protegen
 * los datos están en firebase/firestore.rules y no dependen de este código.
 */
class AccountLinkRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val accountCreator: AccountCreator = FirebaseAccountCreator()
) {
    private val links = db.collection("accountLinks")

    private fun linkRef(primaryUid: String, linkedUid: String) =
        links.document(AccountLink.idFor(primaryUid, linkedUid))

    private fun userRef(uid: String) = db.collection("users").document(uid)
    private fun indexRef(uid: String) = db.collection("accountLinkIndex").document(uid)
    private fun sessionRef(uid: String) = db.collection("accountSessions").document(uid)
    private fun configRef() = db.collection("config").document("accountLinks")

    // ── Consultas ──

    /** Plan de la cuenta. Falla si no se pudo leer: un error nunca se toma como "no es Pro". */
    suspend fun getPlan(uid: String): Result<String> = guarded("leyendo el plan") {
        userRef(uid).get().await().getString(FIELD_ACCOUNT_TYPE) ?: PLAN_NORMAL
    }

    /** Límite vigente. Se cambia en config/accountLinks, sin publicar otra versión. */
    suspend fun getMaxLinked(): Result<Int> = guarded("leyendo el límite") {
        (configRef().get().await().getLong(FIELD_MAX_LINKED)?.toInt())
            ?: AccountLinkRules.DEFAULT_MAX_LINKED
    }

    /** Cuentas que [uid] asoció (pendientes y activas). */
    suspend fun getLinksAsPrimary(uid: String): Result<List<AccountLink>> =
        getLinks("primaryUid", uid)

    /** Invitaciones que [uid] recibió y cuentas que hoy gestionan la suya. */
    suspend fun getLinksAsLinked(uid: String): Result<List<AccountLink>> =
        getLinks("linkedUid", uid)

    private suspend fun getLinks(field: String, uid: String): Result<List<AccountLink>> =
        guarded("leyendo los vínculos") {
            links.whereEqualTo(field, uid).get().await().documents
                .mapNotNull { it.toObject(AccountLink::class.java)?.copy(linkId = it.id) }
                .sortedBy { it.createdAt }
        }

    /** El vínculo en vivo: null cuando deja de existir o ya no se puede leer. */
    fun observeLink(primaryUid: String, linkedUid: String): Flow<AccountLink?> = callbackFlow {
        val registration = linkRef(primaryUid, linkedUid).addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(null)
                // Sin conexión y sin caché no se sabe si existe: no se expulsa por eso
                snapshot == null || (!snapshot.exists() && snapshot.metadata.isFromCache) -> Unit
                else -> trySend(snapshot.toObject(AccountLink::class.java))
            }
        }
        awaitClose { registration.remove() }
    }

    // ── Asociar ──

    /** Invita a una cuenta existente por su correo. Queda pendiente hasta que su titular acepte. */
    suspend fun invite(
        primaryUid: String,
        email: String,
        role: String,
        label: String
    ): Result<Unit> = guarded("invitando una cuenta") {
        AccountLinkRules.checkForm(email, role, label)?.let { throw AccountLinkException(it) }

        // Los correos se guardan tal como se escribieron al registrarse
        val typed = email.trim()
        val linkedUid = db.collection("users")
            .whereIn("email", listOf(typed, typed.lowercase()).distinct())
            .limit(1)
            .get().await()
            .documents.firstOrNull()?.id
            ?: throw AccountLinkException(AccountLinkError.ACCOUNT_NOT_FOUND)

        createLink(primaryUid, linkedUid, typed, role, label)
    }

    /**
     * Crea una cuenta nueva y la deja asociada. La cuenta nueva acepta con su propia sesión (en
     * una FirebaseApp aparte), así que la sesión actual no se cierra y no hay excepciones en las
     * reglas. Si algo falla, se deshace: no quedan cuentas sin perfil.
     */
    suspend fun createAndLink(
        primaryUid: String,
        name: String,
        email: String,
        password: String,
        role: String,
        label: String
    ): Result<Unit> = guarded("creando una cuenta asociada") {
        AccountLinkRules.checkNewAccount(name, email, password, role, label)
            ?.let { throw AccountLinkException(it) }
        // Antes de crear nada: plan y cupo
        preflight(primaryUid)

        // NonCancellable: salir de la pantalla a mitad no puede dejar la cuenta a medio crear
        withContext(NonCancellable) {
            val created = accountCreator.create(email.trim(), password)
            try {
                createLink(primaryUid, created.uid, created.email, role, label)
                try {
                    created.saveProfileAndAccept(name.trim(), primaryUid)
                } catch (e: Exception) {
                    remove(primaryUid, primaryUid, created.uid) // libera el cupo
                    throw e
                }
            } catch (e: Exception) {
                created.rollback()
                throw e
            } finally {
                created.close()
            }
        }
    }

    private suspend fun preflight(primaryUid: String) {
        val current = currentLinkedUids(indexRef(primaryUid).get().await().get(FIELD_LINKED_UIDS))
        val max = getMaxLinked().getOrThrow()
        if (!PlanPolicy.isAllowed(PlanFeature.ASSOCIATED_ACCOUNTS, getPlan(primaryUid).getOrThrow())) {
            throw AccountLinkException(AccountLinkError.NOT_PRO)
        }
        if (AccountLinkRules.isLimitReached(current.size, max)) {
            throw AccountLinkException(AccountLinkError.LIMIT_REACHED)
        }
    }

    private suspend fun createLink(
        primaryUid: String,
        linkedUid: String,
        linkedEmail: String,
        role: String,
        label: String
    ) {
        val linkRef = linkRef(primaryUid, linkedUid)
        val indexRef = indexRef(primaryUid)
        val auditRef = newAuditRef()

        db.runTransaction { tx ->
            val plan = tx.get(userRef(primaryUid)).getString(FIELD_ACCOUNT_TYPE)
            val max = tx.get(configRef()).getLong(FIELD_MAX_LINKED)?.toInt()
                ?: AccountLinkRules.DEFAULT_MAX_LINKED
            val current = currentLinkedUids(tx.get(indexRef).get(FIELD_LINKED_UIDS))

            AccountLinkRules.checkInvite(plan, primaryUid, linkedUid, current, max)
                ?.let { throw AccountLinkException(it) }

            tx.set(
                linkRef,
                mapOf(
                    "linkId" to linkRef.id,
                    "primaryUid" to primaryUid,
                    "linkedUid" to linkedUid,
                    "linkedEmail" to linkedEmail,
                    "role" to role,
                    "label" to label.trim(),
                    "status" to AccountLink.STATUS_PENDING,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "respondedAt" to null
                )
            )
            tx.set(
                indexRef,
                mapOf(
                    FIELD_LINKED_UIDS to FieldValue.arrayUnion(linkedUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            tx.audit(auditRef, primaryUid, primaryUid, linkedUid, AccountAuditAction.INVITED, role)
            null
        }.await()
    }

    // ── Responder y desasociar ──

    /** Solo el titular de la cuenta invitada puede aceptar, y antes de que venza. */
    suspend fun accept(uid: String, primaryUid: String): Result<Unit> = guarded("aceptando") {
        acceptInvitation(db, uid, primaryUid)
    }

    /**
     * Cancela (invitó), rechaza (invitado) o desasocia (cualquiera de los dos) el vínculo.
     * Si era la cuenta activa de quien lo quita, vuelve a su cuenta principal.
     */
    suspend fun remove(actorUid: String, primaryUid: String, linkedUid: String): Result<Unit> =
        guarded("quitando un vínculo") {
            val linkRef = linkRef(primaryUid, linkedUid)
            val auditRef = newAuditRef()
            db.runTransaction { tx ->
                val link = tx.get(linkRef).toObject(AccountLink::class.java)
                    ?.takeIf { actorUid == it.primaryUid || actorUid == it.linkedUid }
                    ?: throw AccountLinkException(AccountLinkError.LINK_NOT_FOUND)
                // Solo el principal puede leer su sesión
                val wasActive = actorUid == primaryUid &&
                    tx.get(sessionRef(actorUid)).getString(FIELD_ACTIVE_UID) == linkedUid

                val action = when {
                    link.status == AccountLink.STATUS_ACTIVE -> AccountAuditAction.UNLINKED
                    actorUid == primaryUid -> AccountAuditAction.CANCELLED
                    else -> AccountAuditAction.DECLINED
                }
                tx.delete(linkRef)
                tx.update(
                    indexRef(primaryUid),
                    mapOf(
                        FIELD_LINKED_UIDS to FieldValue.arrayRemove(linkedUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (wasActive) tx.setSession(actorUid, actorUid)
                tx.audit(auditRef, actorUid, primaryUid, linkedUid, action, link.role)
                null
            }.await()
        }

    // ── Cuenta activa ──

    /** Cambia la cuenta que [ownerUid] gestiona. [targetUid] == [ownerUid] vuelve a la propia. */
    suspend fun switchTo(ownerUid: String, targetUid: String): Result<ActiveAccount> =
        guarded("cambiando de cuenta") {
            val auditRef = newAuditRef()
            db.runTransaction { tx ->
                val target = if (targetUid == ownerUid) {
                    ActiveAccount.own(ownerUid)
                } else {
                    val plan = tx.get(userRef(ownerUid)).getString(FIELD_ACCOUNT_TYPE)
                    if (!PlanPolicy.isAllowed(PlanFeature.ASSOCIATED_ACCOUNTS, plan)) {
                        throw AccountLinkException(AccountLinkError.NOT_PRO)
                    }
                    val link = tx.get(linkRef(ownerUid, targetUid)).toObject(AccountLink::class.java)
                    if (link?.status != AccountLink.STATUS_ACTIVE) {
                        throw AccountLinkException(AccountLinkError.LINK_NOT_FOUND)
                    }
                    ActiveAccount(
                        uid = targetUid,
                        ownerUid = ownerUid,
                        name = tx.get(userRef(targetUid)).getString("name").orEmpty(),
                        role = link.role
                    )
                }
                tx.setSession(ownerUid, targetUid)
                tx.audit(
                    auditRef, ownerUid, ownerUid, targetUid,
                    AccountAuditAction.SWITCHED, target.role.orEmpty()
                )
                target
            }.await()
        }

    /** La cuenta que quedó activa la última vez; si ya no es válida, la propia. */
    suspend fun loadActive(ownerUid: String): Result<ActiveAccount> = guarded("leyendo la cuenta activa") {
        val own = ActiveAccount.own(ownerUid)
        val activeUid = sessionRef(ownerUid).get().await().getString(FIELD_ACTIVE_UID)
        if (activeUid == null || activeUid == ownerUid) return@guarded own
        if (!PlanPolicy.isAllowed(PlanFeature.ASSOCIATED_ACCOUNTS, getPlan(ownerUid).getOrThrow())) {
            return@guarded own
        }
        val link = linkRef(ownerUid, activeUid).get().await().toObject(AccountLink::class.java)
        if (link?.status != AccountLink.STATUS_ACTIVE) return@guarded own
        ActiveAccount(
            uid = activeUid,
            ownerUid = ownerUid,
            name = userRef(activeUid).get().await().getString("name").orEmpty(),
            role = link.role
        )
    }

    // ── Utilidades ──

    private fun newAuditRef() = db.collection("accountAudit").document()

    private fun Transaction.setSession(ownerUid: String, activeUid: String) {
        set(
            sessionRef(ownerUid),
            mapOf(
                FIELD_ACTIVE_UID to activeUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
    }

    private suspend fun <T> guarded(what: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e // cancelación normal de la corrutina: nunca se traga
    } catch (e: Exception) {
        val error = e.toAccountLinkException()
        // Solo el motivo: sin correos ni otros datos personales en el log
        Log.w(TAG, "Error $what: ${error.reason}")
        Result.failure(error)
    }

    companion object {
        private const val TAG = "AccountLinkRepository"
        private const val PLAN_NORMAL = "normal"
        private const val FIELD_ACCOUNT_TYPE = "accountType"
        private const val FIELD_MAX_LINKED = "maxLinkedAccounts"
        private const val FIELD_LINKED_UIDS = "linkedUids"
        private const val FIELD_ACTIVE_UID = "activeUid"
    }
}

/** Acciones que quedan en accountAudit. */
enum class AccountAuditAction(val key: String) {
    INVITED("invited"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    CANCELLED("cancelled"),
    UNLINKED("unlinked"),
    SWITCHED("switched")
}

private fun currentLinkedUids(field: Any?): List<String> =
    (field as? List<*>)?.filterIsInstance<String>().orEmpty()

/** Se escribe en la MISMA transacción que la acción: no hay acción sin registro ni al revés. */
private fun Transaction.audit(
    ref: DocumentReference,
    actorUid: String,
    primaryUid: String,
    linkedUid: String,
    action: AccountAuditAction,
    role: String
) {
    set(
        ref,
        mapOf(
            "actorUid" to actorUid,
            "primaryUid" to primaryUid,
            "linkedUid" to linkedUid,
            "action" to action.key,
            "role" to role,
            "createdAt" to FieldValue.serverTimestamp()
        )
    )
}

/**
 * Acepta la invitación con la sesión de [db]. [onAccept] permite escribir algo más en la misma
 * transacción (al crear una cuenta, su perfil).
 */
internal suspend fun acceptInvitation(
    db: FirebaseFirestore,
    uid: String,
    primaryUid: String,
    onAccept: (Transaction) -> Unit = {}
) {
    val linkRef = db.collection("accountLinks").document(AccountLink.idFor(primaryUid, uid))
    val auditRef = db.collection("accountAudit").document()
    db.runTransaction { tx ->
        val link = tx.get(linkRef).toObject(AccountLink::class.java)
        AccountLinkRules.checkAccept(link, uid)?.let { throw AccountLinkException(it) }
        onAccept(tx)
        tx.update(
            linkRef,
            mapOf(
                "status" to AccountLink.STATUS_ACTIVE,
                "updatedAt" to FieldValue.serverTimestamp(),
                "respondedAt" to FieldValue.serverTimestamp()
            )
        )
        tx.audit(
            auditRef, uid, primaryUid, uid,
            AccountAuditAction.ACCEPTED, link?.role.orEmpty()
        )
        null
    }.await()
}

internal fun Throwable.toAccountLinkException(): AccountLinkException = when (this) {
    is AccountLinkException -> this
    is FirebaseAuthUserCollisionException -> AccountLinkException(AccountLinkError.EMAIL_IN_USE)
    is FirebaseAuthWeakPasswordException -> AccountLinkException(AccountLinkError.WEAK_PASSWORD)
    is FirebaseAuthInvalidCredentialsException -> AccountLinkException(AccountLinkError.INVALID_EMAIL)
    is FirebaseNetworkException -> AccountLinkException(AccountLinkError.NETWORK)
    is FirebaseFirestoreException -> AccountLinkException(
        when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AccountLinkError.NOT_ALLOWED
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AccountLinkError.NETWORK
            else -> AccountLinkError.UNKNOWN
        }
    )
    else -> AccountLinkException(AccountLinkError.UNKNOWN)
}
