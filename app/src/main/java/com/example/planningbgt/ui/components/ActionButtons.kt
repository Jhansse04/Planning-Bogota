package com.example.planningbgt.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planningbgt.model.Event
import com.example.planningbgt.repository.FriendLimitException
import com.example.planningbgt.repository.FriendshipRepository
import com.example.planningbgt.repository.ReservationRepository
import com.example.planningbgt.repository.ReserveButtonState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

// HU-15: botón de amistad (F15-2, F15-4, F15-6, F15-7)
@Composable
fun FriendButton(otherUid: String) {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    if (myUid == null || myUid == otherUid) return

    val repo = remember { FriendshipRepository() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("cargando") }
    var error by remember { mutableStateOf<String?>(null) }
    var showUpgrade by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) = scope.launch {
        try {
            action()
            state = repo.relationState(myUid, otherUid)
            error = null
        } catch (e: FriendLimitException) {
            showUpgrade = true
        } catch (e: Exception) {
            error = e.message ?: "Ocurrió un error"
        }
    }

    LaunchedEffect(otherUid) {
        state = try {
            repo.relationState(myUid, otherUid)
        } catch (e: Exception) {
            "agregar"
        }
    }

    Row {
        when (state) {
            "agregar" -> Button(onClick = { run { repo.sendRequest(myUid, otherUid) } }) {
                Text("Agregar amigo")
            }
            "pendiente" -> OutlinedButton(onClick = { run { repo.rejectOrCancel(myUid, otherUid) } }) {
                Text("Pendiente (cancelar)")
            }
            "responder" -> {
                Button(onClick = { run { repo.accept(myUid, otherUid) } }) {
                    Text("Aceptar")
                }
                OutlinedButton(
                    onClick = { run { repo.rejectOrCancel(myUid, otherUid) } },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Rechazar")
                }
            }
            "amigos" -> Button(onClick = {}, enabled = false) { Text("Amigos") }
            "bloqueado" -> Button(onClick = {}, enabled = false) { Text("No disponible") }
            else -> CircularProgressIndicator()
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (showUpgrade) {
        AlertDialog(
            onDismissRequest = { showUpgrade = false },
            confirmButton = {
                TextButton(onClick = { showUpgrade = false }) { Text("Mejorar a PRO") }
            },
            dismissButton = {
                TextButton(onClick = { showUpgrade = false }) { Text("Ahora no") }
            },
            title = { Text("Límite de amigos alcanzado") },
            text = { Text("Llegaste al máximo de tu plan. Mejora a PRO para agregar más amigos.") }
        )
    }
}

// HU-30: botón de reserva (F30-1, F30-2, F30-5, F30-6)
@Composable
fun ReserveButton(initialEvent: Event) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val repo = remember { ReservationRepository() }
    val scope = rememberCoroutineScope()
    var event by remember(initialEvent.eventId) { mutableStateOf(initialEvent) }
    var error by remember { mutableStateOf<String?>(null) }

    val state = repo.buttonState(event, uid, cancelled = event.status == "cancelled")
    val left = repo.spotsLeft(event)

    if (left != null) Text("Cupos disponibles: $left de ${event.capacity}")

    Button(
        enabled = state == ReserveButtonState.RESERVAR || state == ReserveButtonState.RESERVADO,
        onClick = {
            scope.launch {
                try {
                    if (state == ReserveButtonState.RESERVADO) {
                        repo.cancel(event.eventId, uid)
                        event = event.copy(attendees = event.attendees - uid)
                    } else {
                        repo.reserve(event.eventId, uid)
                        event = event.copy(attendees = event.attendees + uid)
                    }
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Ocurrió un error"
                }
            }
        }
    ) {
        Text(
            when (state) {
                ReserveButtonState.RESERVAR -> "Reservar"
                ReserveButtonState.RESERVADO -> "Cancelar reserva"
                ReserveButtonState.EVENTO_LLENO -> "Evento lleno"
                ReserveButtonState.NO_DISPONIBLE -> "No disponible"
            }
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
