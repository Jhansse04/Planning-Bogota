package com.example.planningbgt.repository

import com.example.planningbgt.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class ProfileSaveException : Exception("No se pudo guardar el perfil del usuario")

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestoreRepo : FirestoreRepository = FirestoreRepository()

    private companion object {
        val REGISTER_ACCOUNT_TYPES = setOf("normal", "pro")
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun login(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("Usuario no encontrado")
            
            // Si el perfil no existe en Firestore aún (por ejemplo de pruebas previas), lo creamos
            try {
                val existing = firestoreRepo.getUser(user.uid)
                if (existing == null) {
                    firestoreRepo.saveUser(
                        User(
                            uid = user.uid,
                            name = email.substringBefore("@"),
                            email = user.email ?: email.trim(),
                            createdAt = Timestamp.now()
                        )
                    )
                }
            } catch (_: Exception) {}

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, pass: String, name: String = "",  accountType: String = "normal"): Result<FirebaseUser> {
         if (accountType !in REGISTER_ACCOUNT_TYPES) {
            return Result.failure(
                IllegalArgumentException("Tipo de cuenta no permitido en el registro: $accountType")
            )
        }
        
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val firebaseUser = result.user ?: throw Exception("No se pudo crear el usuario")

            val newUser = User(
                uid = firebaseUser.uid,
                name = if (name.isNotBlank()) name else email.substringBefore("@"),
                email = email.trim(),
                accountType = accountType,
                createdAt = Timestamp.now()
            )

            val saved = firestoreRepo.saveUser(newUser)
            if (!saved) {
                // Rollback (best effort): sin esto, reintentar el registro falla con
                // "correo ya en uso" y quedaría una cuenta sin perfil.
                try {
                    firebaseUser.delete().await()
                } catch (_: Exception) {
                    auth.signOut() // que al menos no quede la sesión abierta sin perfil
                }
                throw ProfileSaveException()
            }

            Result.success(firebaseUser)
        } 
        catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }
}
