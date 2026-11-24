package com.example.planningbgt.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.planningbgt.model.AccountLink
import com.example.planningbgt.model.ActiveAccount
import com.example.planningbgt.repository.AccountLinkRepository
import com.example.planningbgt.repository.toAccountLinkException
import com.example.planningbgt.util.AccountLinkError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Cuenta que se está gestionando. null solo fuera de MainScreen (por ejemplo, en previews). */
val LocalActiveAccount = compositionLocalOf<ActiveAccount?> { null }

/**
 * HU-45: cuenta activa de la sesión. Vive en MainScreen, por encima de las pestañas, y no depende
 * de Firebase directamente: se prueba con MockK.
 */
@Stable
class ActiveAccountController(
    private val ownerUid: String,
    private val repo: AccountLinkRepository,
    private val scope: CoroutineScope
) {
    /** null mientras se averigua cuál era: no se muestra nada para no mezclar cuentas. */
    var active by mutableStateOf<ActiveAccount?>(null)
        private set

    /** uid al que se está cambiando, o null. */
    var switchingTo by mutableStateOf<String?>(null)
        private set

    var switchError by mutableStateOf<AccountLinkError?>(null)
        private set

    /** Perdió el acceso a la cuenta que gestionaba (por ejemplo, su titular lo revocó). */
    var accessLost by mutableStateOf(false)
        private set

    private var leavingUid: String? = null

    suspend fun load() {
        // Sin conexión o si tarda, la cuenta propia: la app no puede quedar bloqueada por esto
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { repo.loadActive(ownerUid).getOrNull() }
        active = loaded ?: ActiveAccount.own(ownerUid)
    }

    fun switchTo(targetUid: String) {
        if (switchingTo != null || active?.uid == targetUid) return
        accessLost = false
        switchError = null
        // Volver a la propia nunca falla: se aplica ya y se guarda después
        if (targetUid == ownerUid) {
            active = ActiveAccount.own(ownerUid)
        }
        switchingTo = targetUid
        scope.launch {
            try {
                repo.switchTo(ownerUid, targetUid).fold(
                    onSuccess = { active = it },
                    onFailure = {
                        if (targetUid != ownerUid) switchError = it.toAccountLinkException().reason
                    }
                )
            } finally {
                switchingTo = null
            }
        }
    }

    /** Se va a desasociar [uid] desde aquí: si era la activa, se vuelve a la propia sin avisos. */
    fun onUnlinking(uid: String) {
        leavingUid = uid
    }

    fun onUnlinkFinished(uid: String, success: Boolean) {
        leavingUid = null
        if (success && active?.uid == uid) active = ActiveAccount.own(ownerUid)
    }

    fun dismissAccessLost() {
        accessLost = false
    }

    /** Vigila el vínculo mientras se gestiona otra cuenta: si lo revocan, se sale al momento. */
    suspend fun watch(account: ActiveAccount) {
        if (!account.isDelegated) return
        repo.observeLink(ownerUid, account.uid).collect { link ->
            if (active?.uid != account.uid) return@collect
            if (link?.status != AccountLink.STATUS_ACTIVE) {
                accessLost = leavingUid != account.uid
                active = ActiveAccount.own(ownerUid)
            }
        }
    }

    companion object {
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun rememberActiveAccount(ownerUid: String): ActiveAccountController {
    val scope = rememberCoroutineScope()
    val controller = remember(ownerUid) {
        ActiveAccountController(ownerUid, AccountLinkRepository(), scope)
    }
    LaunchedEffect(controller) { controller.load() }
    val active = controller.active
    LaunchedEffect(controller, active?.uid) {
        if (active != null) controller.watch(active)
    }
    return controller
}
