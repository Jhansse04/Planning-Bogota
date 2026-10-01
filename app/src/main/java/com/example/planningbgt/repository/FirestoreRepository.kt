package com.example.planningbgt.repository

import android.util.Log
import com.example.planningbgt.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

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
}
