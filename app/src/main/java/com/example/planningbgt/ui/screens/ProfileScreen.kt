package com.example.planningbgt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onLogout: () -> Unit
) {
    val firestoreRepo = remember { FirestoreRepository() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPrivacySettings by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Cargar datos del usuario desde Firestore
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            user = firestoreRepo.getUser(uid)
            isLoading = false
        }
    }

    // Si se muestra la pantalla de privacidad, renderizar esa pantalla en su lugar
    if (showPrivacySettings) {
        PrivacySettingsScreen(
            currentPrivacy = user?.privacyLevel ?: "public",
            onPrivacyChanged = { newLevel ->
                coroutineScope.launch {
                    currentUser?.uid?.let { uid ->
                        firestoreRepo.updateUser(uid, mapOf("privacyLevel" to newLevel))
                        user = user?.copy(privacyLevel = newLevel)
                    }
                }
            },
            onBack = { showPrivacySettings = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Íconos superiores: Editar y Configuración
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Editar perfil",
                    tint = PrimaryYellowDark
                )
            }
            IconButton(onClick = { showPrivacySettings = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Configuración",
                    tint = PrimaryYellowDark
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Foto de perfil y nombre
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Círculo de foto de perfil
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryYellow),
                contentAlignment = Alignment.Center
            ) {
                if (!user?.profilePictureUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = user?.profilePictureUrl,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Mostrar inicial del nombre
                    Text(
                        text = (user?.name?.firstOrNull()?.toString() ?: "U").uppercase(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Nombre y email
            Column {
                Text(
                    text = user?.name ?: "Cargando...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user?.email ?: currentUser?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Información de la cuenta
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Información de la cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("Tipo de cuenta", when (user?.accountType) {
                    "pro" -> "Pro"
                    "admin" -> "Administrador"
                    else -> "Normal"
                })
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("Privacidad", when (user?.privacyLevel) {
                    "friends" -> "Solo amigos"
                    "private" -> "Nadie"
                    else -> "Todos"
                })
            }
        }

        // Espacio flexible para empujar el botón de cerrar sesión al final
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        // Botón de Cerrar Sesión en rojo (HU08)
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                contentColor = Color.White
            )
        ) {
            Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
        }
    }

    // Diálogo para editar perfil (nombre y URL de foto)
    if (showEditDialog) {
        EditProfileDialog(
            currentName = user?.name ?: "",
            currentPhotoUrl = user?.profilePictureUrl ?: "",
            onDismiss = { showEditDialog = false },
            onSave = { newName, newPhotoUrl ->
                coroutineScope.launch {
                    currentUser?.uid?.let { uid ->
                        val fields = mutableMapOf<String, Any>()
                        if (newName.isNotBlank()) fields["name"] = newName
                        fields["profilePictureUrl"] = newPhotoUrl
                        firestoreRepo.updateUser(uid, fields)
                        user = user?.copy(
                            name = if (newName.isNotBlank()) newName else user?.name ?: "",
                            profilePictureUrl = newPhotoUrl
                        )
                    }
                }
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EditProfileDialog(
    currentName: String,
    currentPhotoUrl: String,
    onDismiss: () -> Unit,
    onSave: (name: String, photoUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var photoUrl by remember { mutableStateOf(currentPhotoUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Perfil") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de usuario") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = photoUrl,
                    onValueChange = { photoUrl = it },
                    label = { Text("URL de foto de perfil") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, photoUrl) }) {
                Text("Guardar", color = PrimaryYellowDark)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
