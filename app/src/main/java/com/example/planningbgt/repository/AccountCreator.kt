package com.example.planningbgt.repository

import com.example.planningbgt.model.User
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/** HU-45: crea cuentas sin cerrar la sesión de quien las está creando. */
interface AccountCreator {
    suspend fun create(email: String, password: String): CreatedAccount
}

/** Cuenta recién creada, con su propia sesión temporal. Siempre se termina con [close]. */
interface CreatedAccount {
    val uid: String
    val email: String

    /** Guarda su perfil y acepta la invitación, todo o nada. */
    suspend fun saveProfileAndAccept(name: String, primaryUid: String)

    /** Borra la cuenta (best effort) para que el correo quede libre y se pueda reintentar. */
    suspend fun rollback()

    suspend fun close()
}

/**
 * createUserWithEmailAndPassword inicia sesión con la cuenta nueva. Por eso se usa una FirebaseApp
 * aparte, en memoria y desechable: la sesión principal no se entera y nada queda en disco.
 */
class FirebaseAccountCreator(
    private val baseApp: () -> FirebaseApp = { FirebaseApp.getInstance() },
    // Para apuntar al emulador en las pruebas
    private val configure: (FirebaseAuth, FirebaseFirestore) -> Unit = { _, _ -> }
) : AccountCreator {

    override suspend fun create(email: String, password: String): CreatedAccount {
        val main = baseApp()
        val app = FirebaseApp.initializeApp(
            main.applicationContext, main.options, "linked-account-${UUID.randomUUID()}"
        )
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        configure(auth, db)

        return try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
                ?: throw IllegalStateException("No se pudo crear el usuario")
            FirebaseCreatedAccount(app, auth, db, user, email)
        } catch (e: Exception) {
            withContext(NonCancellable) { dispose(app, auth, db) }
            throw e
        }
    }

    private class FirebaseCreatedAccount(
        private val app: FirebaseApp,
        private val auth: FirebaseAuth,
        private val db: FirebaseFirestore,
        private val user: FirebaseUser,
        override val email: String
    ) : CreatedAccount {
        override val uid: String = user.uid

        override suspend fun saveProfileAndAccept(name: String, primaryUid: String) {
            val profile = User(uid = uid, name = name, email = email)
            acceptInvitation(db, uid, primaryUid) { tx ->
                tx.set(db.collection("users").document(uid), profile)
            }
        }

        // NonCancellable: si se sale de la pantalla a mitad, igual se deshace y se limpia
        override suspend fun rollback() = withContext(NonCancellable) {
            try {
                user.delete().await()
            } catch (_: Exception) {
            }
            Unit
        }

        override suspend fun close() = withContext(NonCancellable) { dispose(app, auth, db) }
    }

    private companion object {
        suspend fun dispose(app: FirebaseApp, auth: FirebaseAuth, db: FirebaseFirestore) {
            try {
                auth.signOut()
                db.terminate().await()
            } catch (_: Exception) {
            }
            app.delete()
        }
    }
}
