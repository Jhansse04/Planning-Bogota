package com.example.planningbgt.repository

import android.util.Log
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.Friendship
import com.example.planningbgt.model.User
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProAccountRequiredException :
    Exception("Solo las cuentas Pro pueden crear eventos públicos")

class FirestoreRepository {
    private val db : FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Usuarios ──

    suspend fun saveUser(user: User): Boolean {
        return try {
            db.collection("users").document(user.uid).set(user).await()
            Log.d("FirestoreRepository", "Usuario guardado exitosamente en Firestore: ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error guardando usuario en Firestore: ${e.message}", e)
            false
        }
    }

    suspend fun getUser(uid: String): User? {
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo usuario de Firestore: ${e.message}", e)
            null
        }
    }

    suspend fun updateUser(uid: String, fields: Map<String, Any>): Boolean {
        return try {
            db.collection("users").document(uid).update(fields).await()
            Log.d("FirestoreRepository", "Usuario actualizado: $uid")
            true
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error actualizando usuario: ${e.message}", e)
            false
        }
    }

    // ── Eventos ──

    suspend fun getEvents(viewerUid: String? = null): List<Event> {
        return try {
            val snapshot = db.collection("events").get().await()
            val nowMillis = System.currentTimeMillis()
            snapshot.documents
                .mapNotNull { doc -> doc.toObject(Event::class.java)?.copy(eventId = doc.id) }
                .filter { event ->
                    val isVisible = event.eventType != "private" ||
                        (viewerUid != null &&
                            (event.hostId == viewerUid || viewerUid in event.attendees))
                    isVisible && event.date.toDate().time >= nowMillis
                }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo eventos: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun addEvent(event: Event): String? {
        return try {
            val docRef = db.collection("events").add(event).await()
            Log.d("FirestoreRepository", "Evento creado: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error creando evento: ${e.message}", e)
            null
        }
    }

    suspend fun getPanelEvents(uid: String): Result<List<Event>> {
        return try {
            val hosted = db.collection("events").whereEqualTo("hostId", uid).get().await()
            val invited = db.collection("events")
                .whereArrayContains("attendees", uid)
                .whereEqualTo("eventType", "private")
                .get().await()

            val nowMillis = System.currentTimeMillis()
            val events = (hosted.documents + invited.documents)
                .mapNotNull { doc -> doc.toObject(Event::class.java)?.copy(eventId = doc.id) }
                .distinctBy { it.eventId }
                .filterNot { uid in it.hiddenBy }
                .filter { it.date.toDate().time >= nowMillis }
                .sortedBy { it.date }

            Result.success(events)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo eventos del panel: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun createPrivateEvent(event: Event, hostUid: String): Result<String> {
        return try {
            val toSave = event.copy(
                eventType = "private",
                hostId = hostUid,
                hiddenBy = emptyList(),
                reservationEnabled = false
            )
            val docRef = db.collection("events").add(toSave).await()
            Log.d("FirestoreRepository", "Evento privado creado: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error creando evento privado: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun hideEventFromPanel(eventId: String, uid: String): Result<Unit> {
        return try {
            db.collection("events").document(eventId)
                .update("hiddenBy", FieldValue.arrayUnion(uid))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error ocultando evento: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Amistades ──

    suspend fun getAcceptedFriendships(uid: String): Result<List<Friendship>> {
        return try {
            val asUser1 = db.collection("friendships")
                .whereEqualTo("user1Id", uid)
                .whereEqualTo("status", "accepted")
                .get().await()
            val asUser2 = db.collection("friendships")
                .whereEqualTo("user2Id", uid)
                .whereEqualTo("status", "accepted")
                .get().await()

            val friendships = (asUser1.documents + asUser2.documents)                .mapNotNull { doc ->
                    doc.toObject(Friendship::class.java)?.copy(friendshipId = doc.id)
                }
                .distinctBy { it.friendshipId }

            Result.success(friendships)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo amistades: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getLinkedUsers(uid: String): Result<List<User>> {
        return try {
            val friendships = getAcceptedFriendships(uid).getOrThrow()
            val otherIds = friendships
                .map { if (it.user1Id == uid) it.user2Id else it.user1Id }
                .filter { it.isNotBlank() }
                .distinct()
            Result.success(getUsersByIds(otherIds))
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo usuarios enlazados: ${e.message}", e)
            Result.failure(e)
        }
    }

  
    private suspend fun getUsersByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(30).flatMap { chunk ->
            db.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
                .documents
                .mapNotNull { it.toObject(User::class.java) }
        }
    }

        suspend fun createPublicEvent(event: Event, hostUid: String): Result<String> {
        return try {
            val host = getUser(hostUid)
            if (host?.accountType != "pro") throw ProAccountRequiredException()

            val toSave = event.copy(
                eventType = "public",
                hostId = hostUid,
                hiddenBy = emptyList(),
                reservationEnabled = true
            )
            val docRef = db.collection("events").add(toSave).await()
            Log.d("FirestoreRepository", "Evento público creado: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: ProAccountRequiredException) {
            Log.w("FirestoreRepository", "Intento de crear evento público sin cuenta Pro: $hostUid")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error creando evento público: ${e.message}", e)
            Result.failure(e)
        }
    }

    // HU-17: eventos de un amigo (eventos creados + reservas, ordenados por fecha desc)
    suspend fun getFriendEvents(friendUid: String): List<Event> {
        return try {
            val hosted = db.collection("events")
                .whereEqualTo("hostId", friendUid)
                .get().await()
                .documents
                .mapNotNull { it.toObject(Event::class.java)?.copy(eventId = it.id) }

            val attending = db.collection("events")
                .whereArrayContains("attendees", friendUid)
                .get().await()
                .documents
                .mapNotNull { it.toObject(Event::class.java)?.copy(eventId = it.id) }
                .filter { it.eventType != "private" || it.hostId == friendUid }

            (hosted + attending)
                .distinctBy { it.eventId }
                .sortedByDescending { it.date }
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error obteniendo eventos del amigo: ${e.message}", e)
            emptyList()
        }
    }
}

