package com.example.planningbgt.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.DateTimeUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


@Composable
fun UserPanelScreen(
    onBack: () -> Unit,
    onAddEvent: () -> Unit
) {
    val repo = remember { FirestoreRepository() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UserPanelState>(UserPanelState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var removeFailed by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

   
    LaunchedEffect(uid, reloadKey) {
        state = UserPanelState.Loading
        removeFailed = false
        state = if (uid == null) {
            UserPanelState.Loaded(eventsFailed = true, usersFailed = true)
        } else {
            loadUserPanel(repo, uid)
        }
    }

    UserPanelContent(
        state = state,
        removeFailed = removeFailed,
        onBack = onBack,
        onAddEvent = onAddEvent,
        onRemoveEvent = { event ->
            if (uid != null) {
                scope.launch {
                    repo.hideEventFromPanel(event.eventId, uid).fold(
                        onSuccess = {
                            removeFailed = false
                            (state as? UserPanelState.Loaded)?.let { current ->
                                state = current.copy(
                                    events = current.events.filterNot { it.eventId == event.eventId }
                                )
                            }
                        },
                        onFailure = { removeFailed = true }
                    )
                }
            }
        },
        onRetry = { reloadKey++ }
    )
}

/** Versión sin estado ni Firebase: es la que se prueba con Compose Testing. */
@Composable
internal fun UserPanelContent(
    state: UserPanelState,
    removeFailed: Boolean,
    onBack: () -> Unit,
    onAddEvent: () -> Unit,
    onRemoveEvent: (Event) -> Unit,
    onRetry: () -> Unit
) {
    var eventToRemove by remember { mutableStateOf<Event?>(null) }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("panel_list"),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.panel_title),
                    onBack = onBack
                )
            }

            // ── Eventos privados ──
            item(key = "events_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.panel_my_events),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAddEvent,
                        modifier = Modifier.testTag("add_event_button"),
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
                        Text(stringResource(R.string.panel_add_event))
                    }
                }
            }

            when (state) {
                UserPanelState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                                .testTag("panel_loading"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryYellowDark)
                        }
                    }
                }

                is UserPanelState.Loaded -> {
                    when {
                        state.eventsFailed -> item(key = "events_error") {
                            SectionError(
                                message = stringResource(R.string.panel_error_events),
                                onRetry = onRetry
                            )
                        }

                        state.events.isEmpty() -> item(key = "events_empty") {
                            EmptyState(
                                title = stringResource(R.string.panel_empty_events),
                                hint = stringResource(R.string.panel_empty_events_hint)
                            )
                        }

                        else -> items(state.events, key = { "event_${it.eventId}" }) { event ->
                            EventCard(
                                event = event,
                                onRemoveClick = { eventToRemove = event }
                            )
                        }
                    }

                    if (removeFailed) {
                        item(key = "remove_error") {
                            Text(
                                text = stringResource(R.string.panel_remove_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // ── Usuarios enlazados ──
                    item(key = "users_title") {
                        Text(
                            text = stringResource(R.string.panel_linked_users),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }

                    when {
                        state.usersFailed -> item(key = "users_error") {
                            SectionError(
                                message = stringResource(R.string.panel_error_users),
                                onRetry = onRetry
                            )
                        }

                        state.linkedUsers.isEmpty() -> item(key = "users_empty") {
                            EmptyState(
                                title = stringResource(R.string.panel_empty_users),
                                hint = stringResource(R.string.panel_empty_users_hint)
                            )
                        }

                        else -> items(state.linkedUsers, key = { "user_${it.uid}" }) { user ->
                            LinkedUserRow(user)
                        }
                    }
                }
            }
        }
    }

    eventToRemove?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToRemove = null },
            title = { Text(stringResource(R.string.panel_remove_event)) },
            text = { Text(stringResource(R.string.panel_remove_message, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveEvent(event)
                        eventToRemove = null
                    },
                    modifier = Modifier.testTag("confirm_remove_button")
                ) {
                    Text(
                        text = stringResource(R.string.panel_remove_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { eventToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EventCard(event: Event, onRemoveClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DateTimeUtils.formatDateTime(event.date.toDate().time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${categoryLabel(event.category)} · " +
                        stringResource(R.string.panel_capacity, event.capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.testTag("remove_event_${event.eventId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.panel_remove_event),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LinkedUserRow(user: User) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PrimaryYellow),
            contentAlignment = Alignment.Center
        ) {
            if (user.profilePictureUrl.isNotBlank()) {
                AsyncImage(
                    model = user.profilePictureUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = user.name.firstOrNull()?.uppercase() ?: "U",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyState(title: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionError(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_button")) {
            Text(text = stringResource(R.string.panel_retry), color = PrimaryYellowDark)
        }
    }
}