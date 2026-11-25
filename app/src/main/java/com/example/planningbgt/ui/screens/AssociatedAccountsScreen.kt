package com.example.planningbgt.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.planningbgt.R
import com.example.planningbgt.model.AccountLink
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.AccountLinkRepository
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.repository.toAccountLinkException
import com.example.planningbgt.ui.components.ActiveAccountController
import com.example.planningbgt.ui.components.ProUpgradeCard
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.AccountLinkError
import com.example.planningbgt.util.AccountLinkRules
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/** Lo que el usuario puede confirmar sobre un vínculo. */
internal enum class LinkAction { ACCEPT, DECLINE, CANCEL, UNLINK, REVOKE }

/** Acciones de la pantalla, en un solo objeto para que el contenido no dependa de Firebase. */
internal class AssociatedAccountsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onSwitch: (String) -> Unit = {},
    val onLinkAction: (LinkAction, AccountLink) -> Unit = { _, _ -> }
)

/**
 * HU-45: cuentas asociadas. Siempre trabaja con la cuenta que inició sesión, aunque se esté
 * gestionando otra: los vínculos nunca se delegan.
 */
@Composable
fun AssociatedAccountsScreen(
    accountController: ActiveAccountController,
    onBack: () -> Unit
) {
    val linkRepo = remember { AccountLinkRepository() }
    val userRepo = remember { FirestoreRepository() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<AssociatedAccountsState>(AssociatedAccountsState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var actionError by remember { mutableStateOf<AccountLinkError?>(null) }
    var busyLinkId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<AccountLinkError?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(uid, reloadKey) {
        // Al recargar tras una acción se conserva la lista a la vista
        if (state !is AssociatedAccountsState.Loaded) state = AssociatedAccountsState.Loading
        state = if (uid == null) {
            AssociatedAccountsState.Error(AccountLinkError.NOT_ALLOWED)
        } else {
            loadAssociatedAccounts(linkRepo, userRepo, uid)
        }
    }

    fun runLinkAction(action: LinkAction, link: AccountLink) {
        if (uid == null || busyLinkId != null) return
        busyLinkId = link.linkId
        messageRes = null
        actionError = null
        scope.launch {
            if (action == LinkAction.UNLINK) accountController.onUnlinking(link.linkedUid)
            val result = if (action == LinkAction.ACCEPT) {
                linkRepo.accept(uid, link.primaryUid)
            } else {
                linkRepo.remove(uid, link.primaryUid, link.linkedUid)
            }
            result.fold(
                onSuccess = { messageRes = successRes(action) },
                onFailure = { actionError = it.toAccountLinkException().reason }
            )
            busyLinkId = null
            reloadKey++
            if (action == LinkAction.UNLINK) {
                accountController.onUnlinkFinished(link.linkedUid, result.isSuccess)
            }
        }
    }

    AssociatedAccountsContent(
        state = state,
        activeUid = accountController.active?.uid,
        switchingTo = accountController.switchingTo,
        busyLinkId = busyLinkId,
        messageRes = messageRes,
        error = actionError ?: accountController.switchError,
        actions = AssociatedAccountsActions(
            onBack = onBack,
            onRetry = {
                state = AssociatedAccountsState.Loading
                reloadKey++
            },
            onAdd = {
                addError = null
                messageRes = null
                actionError = null
                showAdd = true
            },
            onSwitch = {
                messageRes = null
                actionError = null
                accountController.switchTo(it)
            },
            onLinkAction = ::runLinkAction
        )
    )

    if (showAdd) {
        AddAccountDialog(
            isWorking = isAdding,
            serverError = addError,
            onDismiss = { if (!isAdding) showAdd = false },
            onSubmit = { form ->
                if (uid != null && !isAdding) {
                    isAdding = true
                    addError = null
                    scope.launch {
                        val result = if (form.createNew) {
                            linkRepo.createAndLink(
                                uid, form.name, form.email, form.password, form.role, form.label
                            )
                        } else {
                            linkRepo.invite(uid, form.email, form.role, form.label)
                        }
                        result.fold(
                            onSuccess = {
                                messageRes = if (form.createNew) {
                                    R.string.accounts_created_ok
                                } else {
                                    R.string.accounts_invited_ok
                                }
                                showAdd = false
                            },
                            onFailure = { addError = it.toAccountLinkException().reason }
                        )
                        isAdding = false
                        reloadKey++
                    }
                }
            }
        )
    }
}

private fun successRes(action: LinkAction): Int = when (action) {
    LinkAction.ACCEPT -> R.string.accounts_accepted_ok
    LinkAction.DECLINE -> R.string.accounts_declined_ok
    LinkAction.CANCEL -> R.string.accounts_cancelled_ok
    LinkAction.UNLINK -> R.string.accounts_unlinked_ok
    LinkAction.REVOKE -> R.string.accounts_revoked_ok
}

/** Versión sin estado ni Firebase: es la que se prueba con Compose Testing. */
@Composable
internal fun AssociatedAccountsContent(
    state: AssociatedAccountsState,
    activeUid: String?,
    actions: AssociatedAccountsActions,
    switchingTo: String? = null,
    busyLinkId: String? = null,
    messageRes: Int? = null,
    error: AccountLinkError? = null
) {
    var pending by remember { mutableStateOf<Pair<LinkAction, LinkedAccountItem>?>(null) }
    val ask: (LinkAction, LinkedAccountItem) -> Unit = { action, item -> pending = action to item }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp) // en pantallas anchas no se estira de lado a lado
                    .fillMaxSize()
                    .testTag("accounts_list"),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    ScreenHeader(
                        title = stringResource(R.string.accounts_title),
                        onBack = actions.onBack
                    )
                }

                if (messageRes != null || error != null) {
                    item(key = "message") { StatusMessage(messageRes, error) }
                }

                when (state) {
                    AssociatedAccountsState.Loading -> item(key = "loading") { LoadingRow() }

                    is AssociatedAccountsState.Error -> item(key = "error") {
                        ErrorRow(state.reason, actions.onRetry)
                    }

                    is AssociatedAccountsState.Loaded -> {
                        if (state.invitations.isNotEmpty()) {
                            item(key = "invitations_title") {
                                SectionTitle(stringResource(R.string.accounts_invitations))
                            }
                            items(state.invitations, key = { "inv_${it.link.linkId}" }) { item ->
                                InvitationCard(item, busyLinkId != null, ask)
                            }
                        }

                        if (state.isPro) {
                            item(key = "accounts_title") { AccountsHeader(state, actions.onAdd) }

                            state.own?.let { own ->
                                item(key = "own") {
                                    OwnAccountCard(
                                        own = own,
                                        isActive = activeUid == own.uid,
                                        isSwitching = switchingTo == own.uid,
                                        enabled = switchingTo == null && busyLinkId == null,
                                        onSwitch = { actions.onSwitch(own.uid) }
                                    )
                                }
                            }

                            if (state.accounts.isEmpty()) {
                                item(key = "accounts_empty") { EmptyAccounts() }
                            } else {
                                items(state.accounts, key = { "acc_${it.link.linkId}" }) { item ->
                                    LinkedAccountCard(
                                        item = item,
                                        isActive = activeUid == item.link.linkedUid,
                                        isSwitching = switchingTo == item.link.linkedUid,
                                        enabled = switchingTo == null && busyLinkId == null,
                                        onSwitch = { actions.onSwitch(item.link.linkedUid) },
                                        ask = ask
                                    )
                                }
                            }
                        } else {
                            item(key = "upgrade") {
                                ProUpgradeCard(
                                    message = stringResource(R.string.accounts_not_pro_message),
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }

                        if (state.managers.isNotEmpty()) {
                            item(key = "managers_title") {
                                SectionTitle(stringResource(R.string.accounts_managers))
                            }
                            items(state.managers, key = { "man_${it.link.linkId}" }) { item ->
                                ManagerCard(item, busyLinkId != null, ask)
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { (action, item) ->
        ConfirmLinkActionDialog(
            action = action,
            item = item,
            onConfirm = {
                pending = null
                actions.onLinkAction(action, item.link)
            },
            onDismiss = { pending = null }
        )
    }
}

// ── Piezas ──

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(top = 16.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsHeader(state: AssociatedAccountsState.Loaded, onAdd: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // FlowRow: con la letra grande, el botón baja en vez de recortarse
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(stringResource(R.string.accounts_section))
            Button(
                onClick = onAdd,
                enabled = !state.limitReached,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("add_account_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryYellow,
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.accounts_add))
            }
        }
        Text(
            text = if (state.maxLinked == AccountLinkRules.UNLIMITED) {
                stringResource(R.string.accounts_count_unlimited, state.accounts.size)
            } else {
                stringResource(R.string.accounts_count, state.accounts.size, state.maxLinked)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.limitReached) {
            Text(
                text = stringResource(R.string.accounts_limit_reached, state.maxLinked),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("limit_reached")
            )
        }
    }
}

@Composable
private fun StatusMessage(messageRes: Int?, error: AccountLinkError?) {
    val isError = error != null
    Text(
        text = if (error != null) accountErrorText(error) else stringResource(messageRes ?: return),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(if (isError) "accounts_error_message" else "accounts_success_message")
    )
}

@Composable
private fun LoadingRow() {
    val label = stringResource(R.string.accounts_loading)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .testTag("accounts_loading"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = PrimaryYellowDark,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun ErrorRow(reason: AccountLinkError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.accounts_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = accountErrorText(reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag("accounts_retry_button"),
            colors = onSurfaceTextButton()
        ) {
            Text(stringResource(R.string.panel_retry), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyAccounts() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.accounts_empty),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.accounts_empty_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OwnAccountCard(
    own: User,
    isActive: Boolean,
    isSwitching: Boolean,
    enabled: Boolean,
    onSwitch: () -> Unit
) {
    AccountCard(
        name = own.name,
        email = own.email,
        photoUrl = own.profilePictureUrl,
        detail = stringResource(R.string.accounts_own)
    ) {
        SwitchControl(own.uid, isActive, isSwitching, enabled, onSwitch)
    }
}

@Composable
private fun LinkedAccountCard(
    item: LinkedAccountItem,
    isActive: Boolean,
    isSwitching: Boolean,
    enabled: Boolean,
    onSwitch: () -> Unit,
    ask: (LinkAction, LinkedAccountItem) -> Unit
) {
    val link = item.link
    val active = link.status == AccountLink.STATUS_ACTIVE
    val status = stringResource(
        when {
            active -> R.string.accounts_status_active
            item.expired -> R.string.accounts_status_expired
            else -> R.string.accounts_status_pending
        }
    )
    AccountCard(
        name = itemName(item),
        email = link.linkedEmail,
        photoUrl = item.profile?.profilePictureUrl.orEmpty(),
        detail = listOf(link.label, roleLabel(link.role), status)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    ) {
        if (active) SwitchControl(link.linkedUid, isActive, isSwitching, enabled, onSwitch)
        val action = if (active) LinkAction.UNLINK else LinkAction.CANCEL
        TextButton(
            onClick = { ask(action, item) },
            enabled = enabled,
            modifier = Modifier.testTag("remove_${link.linkedUid}"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(
                stringResource(
                    if (active) R.string.accounts_unlink else R.string.accounts_cancel_invitation
                )
            )
        }
    }
}

/** Selector de cuenta activa. */
@Composable
private fun SwitchControl(
    uid: String,
    isActive: Boolean,
    isSwitching: Boolean,
    enabled: Boolean,
    onSwitch: () -> Unit
) {
    if (isActive) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp) // misma altura que el botón que reemplaza
                .testTag("active_$uid"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null, // decorativo: el texto ya lo dice
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.accounts_active),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Button(
            onClick = onSwitch,
            enabled = enabled,
            modifier = Modifier.testTag("switch_$uid"),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryYellow,
                contentColor = TextPrimary
            )
        ) {
            Text(
                stringResource(
                    if (isSwitching) R.string.accounts_switching else R.string.accounts_switch
                )
            )
        }
    }
}

@Composable
private fun InvitationCard(
    item: LinkedAccountItem,
    busy: Boolean,
    ask: (LinkAction, LinkedAccountItem) -> Unit
) {
    AccountCard(
        name = itemName(item),
        email = item.profile?.email.orEmpty(),
        photoUrl = item.profile?.profilePictureUrl.orEmpty(),
        detail = roleLabel(item.link.role)
    ) {
        Button(
            onClick = { ask(LinkAction.ACCEPT, item) },
            enabled = !busy,
            modifier = Modifier.testTag("accept_${item.link.primaryUid}"),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryYellow,
                contentColor = TextPrimary
            )
        ) {
            Text(stringResource(R.string.accounts_accept))
        }
        TextButton(
            onClick = { ask(LinkAction.DECLINE, item) },
            enabled = !busy,
            modifier = Modifier.testTag("decline_${item.link.primaryUid}"),
            colors = onSurfaceTextButton()
        ) {
            Text(stringResource(R.string.accounts_decline))
        }
    }
}

@Composable
private fun ManagerCard(
    item: LinkedAccountItem,
    busy: Boolean,
    ask: (LinkAction, LinkedAccountItem) -> Unit
) {
    AccountCard(
        name = itemName(item),
        email = item.profile?.email.orEmpty(),
        photoUrl = item.profile?.profilePictureUrl.orEmpty(),
        detail = roleLabel(item.link.role)
    ) {
        TextButton(
            onClick = { ask(LinkAction.REVOKE, item) },
            enabled = !busy,
            modifier = Modifier.testTag("revoke_${item.link.primaryUid}"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.accounts_revoke))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(
    name: String,
    email: String,
    photoUrl: String,
    detail: String,
    controls: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // TalkBack lee la tarjeta como una sola frase
                modifier = Modifier.semantics(mergeDescendants = true) {}
            ) {
                AccountAvatar(name, photoUrl)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (email.isNotBlank() && email != name) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                controls()
            }
        }
    }
}

@Composable
private fun AccountAvatar(name: String, photoUrl: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PrimaryYellow),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null, // decorativo: el nombre está al lado
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "U",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ConfirmLinkActionDialog(
    action: LinkAction,
    item: LinkedAccountItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = itemName(item)
    val (titleRes, messageRes, confirmRes) = when (action) {
        LinkAction.ACCEPT -> Triple(
            R.string.accounts_accept_title,
            if (item.link.role == AccountLink.ROLE_ACT) {
                R.string.accounts_accept_message_act
            } else {
                R.string.accounts_accept_message_manage
            },
            R.string.accounts_accept
        )
        LinkAction.DECLINE -> Triple(
            R.string.accounts_decline_title, R.string.accounts_decline_message, R.string.accounts_decline
        )
        LinkAction.CANCEL -> Triple(
            R.string.accounts_cancel_title,
            R.string.accounts_cancel_message,
            R.string.accounts_cancel_invitation
        )
        LinkAction.UNLINK -> Triple(
            R.string.accounts_unlink_title, R.string.accounts_unlink_message, R.string.accounts_unlink
        )
        LinkAction.REVOKE -> Triple(
            R.string.accounts_revoke_title, R.string.accounts_revoke_message, R.string.accounts_revoke
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_link_action"),
                colors = if (action == LinkAction.ACCEPT) {
                    onSurfaceTextButton()
                } else {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                }
            ) {
                Text(stringResource(confirmRes), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = onSurfaceTextButton()) {
                Text(
                    stringResource(
                        // "Cancelar" junto a "Cancelar invitación" sería ambiguo
                        if (action == LinkAction.CANCEL) R.string.common_back else R.string.common_cancel
                    )
                )
            }
        }
    )
}

// ── Textos ──

@Composable
private fun itemName(item: LinkedAccountItem): String =
    item.profile?.name?.takeIf { it.isNotBlank() }
        ?: item.link.linkedEmail.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.accounts_unknown_name)

@Composable
internal fun roleLabel(role: String): String = stringResource(
    if (role == AccountLink.ROLE_ACT) R.string.accounts_role_act else R.string.accounts_role_manage
)

/** Texto (strings.xml) para cada motivo. */
@Composable
internal fun accountErrorText(error: AccountLinkError): String = when (error) {
    AccountLinkError.NOT_PRO -> stringResource(R.string.accounts_error_not_pro)
    AccountLinkError.INVALID_EMAIL -> stringResource(R.string.accounts_error_email)
    AccountLinkError.INVALID_ROLE -> stringResource(R.string.accounts_error_role)
    AccountLinkError.LABEL_TOO_LONG ->
        stringResource(R.string.error_too_long, AccountLinkRules.LABEL_MAX_LENGTH)
    AccountLinkError.NAME_REQUIRED ->
        stringResource(R.string.accounts_error_name, AccountLinkRules.NAME_MAX_LENGTH)
    AccountLinkError.WEAK_PASSWORD ->
        stringResource(R.string.accounts_error_password, AccountLinkRules.PASSWORD_MIN_LENGTH)
    AccountLinkError.EMAIL_IN_USE -> stringResource(R.string.accounts_error_email_in_use)
    AccountLinkError.ACCOUNT_NOT_FOUND -> stringResource(R.string.accounts_error_not_found)
    AccountLinkError.SELF_LINK -> stringResource(R.string.accounts_error_self)
    AccountLinkError.ALREADY_LINKED -> stringResource(R.string.accounts_error_duplicate)
    AccountLinkError.LIMIT_REACHED -> stringResource(R.string.accounts_error_limit)
    AccountLinkError.INVITATION_EXPIRED -> stringResource(R.string.accounts_error_expired)
    AccountLinkError.LINK_NOT_FOUND -> stringResource(R.string.accounts_error_link_gone)
    AccountLinkError.NOT_ALLOWED -> stringResource(R.string.accounts_error_not_allowed)
    AccountLinkError.NETWORK -> stringResource(R.string.accounts_error_network)
    AccountLinkError.UNKNOWN -> stringResource(R.string.accounts_error_generic)
}

@Composable
internal fun onSurfaceTextButton() =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
