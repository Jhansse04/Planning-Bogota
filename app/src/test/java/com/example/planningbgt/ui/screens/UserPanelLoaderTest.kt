package com.example.planningbgt.ui.screens

import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.repository.FirestoreRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPanelLoaderTest {

    private val repo = mockk<FirestoreRepository>()
    private val uid = "uid123"

    private val events = listOf(Event(eventId = "e1", title = "Plan 1"))
    private val users = listOf(User(uid = "u2", name = "Ana"))

    @Test
    fun `si todo sale bien devuelve eventos y usuarios`() = runTest {
        coEvery { repo.getPanelEvents(uid) } returns Result.success(events)
        coEvery { repo.getLinkedUsers(uid) } returns Result.success(users)

        val result = loadUserPanel(repo, uid)

        assertEquals(events, result.events)
        assertEquals(users, result.linkedUsers)
        assertFalse(result.eventsFailed)
        assertFalse(result.usersFailed)
    }

    @Test
    fun `si fallan los eventos los usuarios se muestran igual`() = runTest {
        coEvery { repo.getPanelEvents(uid) } returns Result.failure(Exception("PERMISSION_DENIED"))
        coEvery { repo.getLinkedUsers(uid) } returns Result.success(users)

        val result = loadUserPanel(repo, uid)

        assertTrue(result.eventsFailed)
        assertTrue(result.events.isEmpty())
        assertFalse(result.usersFailed)
        assertEquals(users, result.linkedUsers)
    }

    @Test
    fun `si fallan los usuarios los eventos se muestran igual`() = runTest {
        coEvery { repo.getPanelEvents(uid) } returns Result.success(events)
        coEvery { repo.getLinkedUsers(uid) } returns Result.failure(Exception("offline"))

        val result = loadUserPanel(repo, uid)

        assertFalse(result.eventsFailed)
        assertEquals(events, result.events)
        assertTrue(result.usersFailed)
        assertTrue(result.linkedUsers.isEmpty())
    }

    @Test
    fun `si todo falla marca ambos errores`() = runTest {
        coEvery { repo.getPanelEvents(uid) } returns Result.failure(Exception("x"))
        coEvery { repo.getLinkedUsers(uid) } returns Result.failure(Exception("y"))

        val result = loadUserPanel(repo, uid)

        assertTrue(result.eventsFailed)
        assertTrue(result.usersFailed)
    }

    @Test
    fun `consulta el repositorio con el uid correcto`() = runTest {
        coEvery { repo.getPanelEvents(any()) } returns Result.success(emptyList())
        coEvery { repo.getLinkedUsers(any()) } returns Result.success(emptyList())

        loadUserPanel(repo, uid)

        coVerify(exactly = 1) { repo.getPanelEvents(uid) }
        coVerify(exactly = 1) { repo.getLinkedUsers(uid) }
    }
}
