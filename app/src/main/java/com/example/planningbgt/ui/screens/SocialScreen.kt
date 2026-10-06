package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import com.example.planningbgt.repository.FriendshipRepository
import com.example.planningbgt.ui.components.FriendButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SocialScreen() {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val friendRepo = remember { FriendshipRepository() }
    val userRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val titles = listOf("Buscar", "Recibidas", "Enviadas", "Amigos")

    suspend fun loadTab(index: Int) {
        loading = true
        message = null
        try {
            people = when (index) {
                1 -> friendRepo.received(myUid).mapNotNull { userRepo.getUser(it.user1Id) }
                2 -> friendRepo.sent(myUid).mapNotNull { userRepo.getUser(it.user2Id) }
                3 -> friendRepo.friends(myUid).mapNotNull {
                    userRepo.getUser(if (it.user1Id == myUid) it.user2Id else it.user1Id)
                }
                else -> emptyList()
            }
            if (index != 0 && people.isEmpty()) message = "No hay nada por aquí todavía"
        } catch (e: Exception) {
            message = e.message ?: "Error cargando datos"
        }
        loading = false
    }

    LaunchedEffect(tab) { if (tab == 0) people = emptyList() else loadTab(tab) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }

        if (tab == 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por nombre") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch {
                        loading = true
                        message = null
                        try {
                            people = friendRepo.searchUsers(myUid, query.trim())
                            if (people.isEmpty()) message = "Sin resultados"
                        } catch (e: Exception) {
                            message = e.message ?: "Error en la búsqueda"
                        }
                        loading = false
                    }
                }) { Text("Buscar") }
            }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, modifier = Modifier.padding(16.dp)) }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people, key = { it.uid }) { person ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        FriendButton(person.uid)
                    }
                }
            }
        }
    }
}
