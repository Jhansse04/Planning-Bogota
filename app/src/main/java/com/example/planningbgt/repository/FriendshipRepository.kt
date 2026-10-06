package com.example.planningbgt.repository

import com.example.planningbgt.model.Friendship
import com.example.planningbgt.model.User
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FriendshipRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val col = db.collection("friendships") // ajustar si el equipo usa otro nombre

    // Mismo ID para la pareja, sin importar quién escribe primero
    private fun pairId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    companion object {
        const val LIMIT_NORMAL = 50  // TODO: confirmar el valor real en HU-18
        const val LIMIT_PRO = 500    // TODO: confirmar el valor real en HU-18
    }

    private suspend fun countFriends(uid: String): Int {
        val a = col.whereEqualTo("user1Id", uid).whereEqualTo("status", "accepted")
            .count().get(AggregateSource.SERVER).await().count
        val b = col.whereEqualTo("user2Id", uid).whereEqualTo("status", "accepted")
            .count().get(AggregateSource.SERVER).await().count
        return (a + b).toInt()
    }

    // F15-8: lanza FriendLimitException si el usuario alcanzó el límite de su plan
    private suspend fun checkLimit(uid: String) {
        val type = db.collection("users").document(uid).get().await()
            .getString("accountType") ?: "normal"
        val limit = when (type) {
            "admin" -> return          // sin límite
            "pro" -> LIMIT_PRO
            else -> LIMIT_NORMAL
        }
        if (countFriends(uid) >= limit) {
            throw FriendLimitException("Alcanzaste el límite de amigos de tu plan. ¡Mejora a PRO!")
        }
    }

    // F15-1: buscar por nombre respetando privacidad (HU-05)
    suspend fun searchUsers(myUid: String, text: String): List<User> {
        if (text.isBlank()) return emptyList()
        val found = db.collection("users").orderBy("name")
            .startAt(text).endAt(text + "\uf8ff").limit(30)
            .get().await().toObjects(User::class.java)
        val friendIds = friends(myUid)
            .map { if (it.user1Id == myUid) it.user2Id else it.user1Id }.toSet()
        return found.filter { it.uid != myUid }.filter {
            when (it.privacyLevel) {
                "public" -> true
                "friends" -> it.uid in friendIds
                else -> false // private
            }
        }
    }

    // F15-2 y F15-3: enviar, sin autosolicitud ni duplicados ni amigos previos
    suspend fun sendRequest(myUid: String, otherUid: String) {
        require(myUid != otherUid) { "No puedes agregarte a ti mismo" }
        checkLimit(myUid)
        val id = pairId(myUid, otherUid)
        val ref = col.document(id)
        db.runTransaction { tx ->
            if (tx.get(ref).exists()) {
                throw IllegalStateException("Ya existe una solicitud o una amistad")
            }
            tx.set(ref, Friendship(friendshipId = id, user1Id = myUid, user2Id = otherUid, status = "pending"))
            null
        }.await()
    }

    // F15-6: solo el receptor puede aceptar
    suspend fun accept(myUid: String, otherUid: String) {
        checkLimit(myUid)
        val ref = col.document(pairId(myUid, otherUid))
        db.runTransaction { tx ->
            val f = tx.get(ref).toObject(Friendship::class.java)
                ?: throw IllegalStateException("La solicitud no existe")
            if (f.user2Id != myUid || f.status != "pending") {
                throw IllegalStateException("Solicitud no válida")
            }
            tx.update(ref, "status", "accepted")
            null
        }.await()
    }

    // F15-6 y F15-7: rechazar (receptor) o cancelar (emisor) = borrar la solicitud pendiente
    suspend fun rejectOrCancel(myUid: String, otherUid: String) {
        val ref = col.document(pairId(myUid, otherUid))
        db.runTransaction { tx ->
            val f = tx.get(ref).toObject(Friendship::class.java) ?: return@runTransaction null
            if (f.status == "pending" && (f.user1Id == myUid || f.user2Id == myUid)) tx.delete(ref)
            null
        }.await()
    }

    // F15-4: estado del botón en el perfil
    suspend fun relationState(myUid: String, otherUid: String): String {
        val f = col.document(pairId(myUid, otherUid)).get().await()
            .toObject(Friendship::class.java) ?: return "agregar"
        return when {
            f.status == "accepted" -> "amigos"
            f.status == "blocked" -> "bloqueado"
            f.user1Id == myUid -> "pendiente"
            else -> "responder"
        }
    }

    // F15-5: bandeja de recibidas y enviadas
    suspend fun received(myUid: String) = col.whereEqualTo("user2Id", myUid)
        .whereEqualTo("status", "pending").get().await().toObjects(Friendship::class.java)

    suspend fun sent(myUid: String) = col.whereEqualTo("user1Id", myUid)
        .whereEqualTo("status", "pending").get().await().toObjects(Friendship::class.java)

    // Lista de amigos: el usuario puede estar en cualquiera de los dos campos
    suspend fun friends(myUid: String): List<Friendship> {
        val a = col.whereEqualTo("user1Id", myUid).whereEqualTo("status", "accepted").get().await()
        val b = col.whereEqualTo("user2Id", myUid).whereEqualTo("status", "accepted").get().await()
        return a.toObjects(Friendship::class.java) + b.toObjects(Friendship::class.java)
    }
}

class FriendLimitException(message: String) : Exception(message)
