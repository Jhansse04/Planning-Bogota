package com.example.planningbgt.ui.screens

import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

sealed interface UserPanelState {
    data object Loading : UserPanelState

    data class Loaded(
        val events: List<Event> = emptyList(),
        val linkedUsers: List<User> = emptyList(),
        val eventsFailed: Boolean = false,
        val usersFailed: Boolean = false
    ) : UserPanelState
}

/**
 * Carga eventos y usuarios enlazados en paralelo. Cada sección falla por separado.
 * Vive fuera del composable para poder testearla con MockK.
 */
suspend fun loadUserPanel(repo: FirestoreRepository, uid: String): UserPanelState.Loaded =
    coroutineScope {
        val eventsDeferred = async { repo.getPanelEvents(uid) }
        val usersDeferred = async { repo.getLinkedUsers(uid) }
        val events = eventsDeferred.await()
        val users = usersDeferred.await()

        UserPanelState.Loaded(
            events = events.getOrDefault(emptyList()),
            linkedUsers = users.getOrDefault(emptyList()),
            eventsFailed = events.isFailure,
            usersFailed = users.isFailure
        )
    }