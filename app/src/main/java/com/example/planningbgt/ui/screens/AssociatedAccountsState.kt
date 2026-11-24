package com.example.planningbgt.ui.screens

import com.example.planningbgt.model.AccountLink
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.AccountLinkRepository
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.repository.toAccountLinkException
import com.example.planningbgt.util.AccountLinkError
import com.example.planningbgt.util.AccountLinkRules
import com.example.planningbgt.util.PlanFeature
import com.example.planningbgt.util.PlanPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Vínculo + perfil de la OTRA parte (null si no se puede o no se debe mostrar). */
data class LinkedAccountItem(
    val link: AccountLink,
    val profile: User? = null,
    val expired: Boolean = false
)

sealed interface AssociatedAccountsState {
    data object Loading : AssociatedAccountsState

    data class Error(val reason: AccountLinkError) : AssociatedAccountsState

    data class Loaded(
        val isPro: Boolean = false,
        val maxLinked: Int = AccountLinkRules.DEFAULT_MAX_LINKED,
        val own: User? = null,
        /** Cuentas que este usuario asoció. Siempre vacía si no es Pro. */
        val accounts: List<LinkedAccountItem> = emptyList(),
        /** Invitaciones recibidas sin responder. Las puede responder cualquiera, sea Pro o no. */
        val invitations: List<LinkedAccountItem> = emptyList(),
        /** Quiénes gestionan hoy la cuenta de este usuario. */
        val managers: List<LinkedAccountItem> = emptyList()
    ) : AssociatedAccountsState {
        val limitReached: Boolean
            get() = AccountLinkRules.isLimitReached(accounts.size, maxLinked)
    }
}

/**
 * Carga todo en paralelo. Vive fuera del composable para poder testearla con MockK.
 * Si algo falla se devuelve Error: nunca se muestra una lista incompleta ni un plan supuesto.
 */
suspend fun loadAssociatedAccounts(
    linkRepo: AccountLinkRepository,
    userRepo: FirestoreRepository,
    uid: String,
    nowMillis: Long = System.currentTimeMillis()
): AssociatedAccountsState = coroutineScope {
    val planDeferred = async { linkRepo.getPlan(uid) }
    val maxDeferred = async { linkRepo.getMaxLinked() }
    val asLinkedDeferred = async { linkRepo.getLinksAsLinked(uid) }
    val ownDeferred = async { userRepo.getUser(uid) }

    val isPro = PlanPolicy.isAllowed(
        PlanFeature.ASSOCIATED_ACCOUNTS,
        planDeferred.await().getOrElse {
            return@coroutineScope AssociatedAccountsState.Error(it.toAccountLinkException().reason)
        }
    )
    val maxLinked = maxDeferred.await().getOrElse {
        return@coroutineScope AssociatedAccountsState.Error(it.toAccountLinkException().reason)
    }
    val asLinked = asLinkedDeferred.await().getOrElse {
        return@coroutineScope AssociatedAccountsState.Error(it.toAccountLinkException().reason)
    }
    val asPrimary = if (isPro) {
        linkRepo.getLinksAsPrimary(uid).getOrElse {
            return@coroutineScope AssociatedAccountsState.Error(it.toAccountLinkException().reason)
        }
    } else {
        emptyList()
    }

    suspend fun items(links: List<AccountLink>, otherUid: (AccountLink) -> String) =
        links.map { link ->
            async {
                val profile = userRepo.getUser(otherUid(link))
                LinkedAccountItem(
                    link = link,
                    profile = profile?.takeIf { canShowProfile(link, it, uid) },
                    expired = AccountLinkRules.isExpired(link, nowMillis)
                )
            }
        }.awaitAll()

    val linkedItems = items(asLinked) { it.primaryUid }
    AssociatedAccountsState.Loaded(
        isPro = isPro,
        maxLinked = maxLinked,
        own = ownDeferred.await(),
        accounts = items(asPrimary) { it.linkedUid },
        // Una invitación vencida ya no se puede aceptar: no se le muestra a quien la recibió
        invitations = linkedItems.filter {
            it.link.status == AccountLink.STATUS_PENDING && !it.expired
        },
        managers = linkedItems.filter { it.link.status == AccountLink.STATUS_ACTIVE }
    )
}

/**
 * Invitar no da derecho a ver el perfil: mientras no acepte, solo se muestra si es público.
 * Quien recibe la invitación sí ve quién lo invita; lo necesita para decidir.
 */
private fun canShowProfile(link: AccountLink, profile: User, viewerUid: String): Boolean =
    link.linkedUid == viewerUid ||
        link.status == AccountLink.STATUS_ACTIVE ||
        profile.privacyLevel == "public"
