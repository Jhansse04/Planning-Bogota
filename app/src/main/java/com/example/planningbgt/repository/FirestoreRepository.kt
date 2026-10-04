package com.example.planningbgt.repository

import android.util.Log
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

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

    suspend fun getEvents(): List<Event> {
        return try {
            val snapshot = db.collection("events").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Event::class.java)?.copy(eventId = doc.id)
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
}
