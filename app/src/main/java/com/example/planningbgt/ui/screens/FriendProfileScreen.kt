package com.example.planningbgt.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.repository.FriendshipRepository
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// HU-17 + HU-18: Perfil público de un amigo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileScreen(
    friendUid: String,
    onBack: () -> Unit
) {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val firestoreRepo = remember { FirestoreRepository() }
    val friendRepo = remember { FriendshipRepository() }
    val scope = rememberCoroutineScope()

    var friend by remember { mutableStateOf<User?>(null) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showConfirmRemove by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf(false) }

    LaunchedEffect(friendUid) {
        loading = true
        friend = firestoreRepo.getUser(friendUid)
        events = firestoreRepo.getFriendEvents(friendUid)
        loading = false
    }

    // Si se eliminó el amigo, regresar automáticamente
    LaunchedEffect(removed) {
        if (removed) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(friend?.name ?: "Perfil", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    // HU-18: botón eliminar amigo (esquina derecha)
                    IconButton(onClick = { showConfirmRemove = true }) {
                        Icon(
                            imageVector = Icons.Filled.PersonRemove,
                            contentDescription = "Eliminar amigo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryYellow,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryYellowDark)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Cabecera: foto + nombre ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Foto de perfil
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(PrimaryYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        val url = friend?.profilePictureUrl
                        if (!url.isNullOrBlank()) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Foto de ${friend?.name}",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (friend?.name?.firstOrNull()?.toString() ?: "?").uppercase(),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Nombre del amigo
                    Text(
                        text = friend?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // ── Sección: eventos ──
            item {
                Text(
                    text = "Eventos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = "Este usuario no tiene eventos registrados todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(events, key = { it.eventId }) { event ->
                    FriendEventCard(event = event, friendUid = friendUid)
                }
            }

            // Error de eliminación
            removeError?.let {
                item {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }

    // ── Diálogo confirmación eliminar amigo (HU-18) ──
    if (showConfirmRemove) {
        AlertDialog(
            onDismissRequest = { showConfirmRemove = false },
            title = { Text("Eliminar amigo") },
            text = {
                Text(
                    "¿Deseas eliminar a ${friend?.name ?: "este usuario"} de tu lista de amigos? " +
                    "Podrás volver a enviarle una solicitud en el futuro."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRemove = false
                        scope.launch {
                            try {
                                friendRepo.removeFriend(myUid, friendUid)
                                removed = true
                            } catch (e: Exception) {
                                removeError = e.message ?: "Error al eliminar amigo"
                            }
                        }
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRemove = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// Tarjeta de evento dentro del perfil del amigo
@Composable
private fun FriendEventCard(event: Event, friendUid: String) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CO")) }
    val isHost = event.hostId == friendUid
    val typeLabel = when (event.eventType) {
        "private" -> "Privado"
        "paid_promotion" -> "De pago"
        else -> "Público"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.eventType == "private")
                        MaterialTheme.colorScheme.secondary
                    else
                        PrimaryYellowDark,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fmt.format(event.date.toDate()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isHost) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Anfitrión",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryYellowDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
