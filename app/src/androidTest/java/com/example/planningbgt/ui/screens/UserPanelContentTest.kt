package com.example.planningbgt.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.planningbgt.R
import com.example.planningbgt.model.Event
import com.example.planningbgt.model.User
import com.example.planningbgt.ui.theme.PlanningBGTTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UserPanelContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val event = Event(eventId = "e1", title = "Fiesta de prueba", category = "fiesta", capacity = 20)
    private val user = User(uid = "u1", name = "Ana Gómez")

    private fun str(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private fun setPanel(
        state: UserPanelState,
        removeFailed: Boolean = false,
        onBack: () -> Unit = {},
        onAddEvent: () -> Unit = {},
        onRemoveEvent: (Event) -> Unit = {},
        onRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            PlanningBGTTheme {
                UserPanelContent(
                    state = state,
                    removeFailed = removeFailed,
                    onBack = onBack,
                    onAddEvent = onAddEvent,
                    onRemoveEvent = onRemoveEvent,
                    onRetry = onRetry
                )
            }
        }
    }

    @Test
    fun loadingMuestraIndicador() {
        setPanel(UserPanelState.Loading)
        composeRule.onNodeWithTag("panel_loading").assertExists()
    }

    @Test
    fun sinDatosMuestraEmptyStates() {
        setPanel(UserPanelState.Loaded())
        composeRule.onNodeWithText(str(R.string.panel_empty_events)).assertExists()
        composeRule.onNodeWithTag("panel_list")
            .performScrollToNode(hasText(str(R.string.panel_empty_users)))
        composeRule.onNodeWithText(str(R.string.panel_empty_users)).assertExists()
    }

    @Test
    fun conDatosMuestraEventosYUsuarios() {
        setPanel(UserPanelState.Loaded(events = listOf(event), linkedUsers = listOf(user)))
        composeRule.onNodeWithText("Fiesta de prueba").assertExists()
        composeRule.onNodeWithTag("panel_list").performScrollToNode(hasText("Ana Gómez"))
        composeRule.onNodeWithText("Ana Gómez").assertExists()
    }

    @Test
    fun errorDeEventosMuestraReintentarYLlamaCallback() {
        var retried = false
        setPanel(UserPanelState.Loaded(eventsFailed = true), onRetry = { retried = true })

        composeRule.onNodeWithText(str(R.string.panel_error_events)).assertExists()
        composeRule.onNodeWithTag("retry_button").performClick()

        assertTrue(retried)
    }

    @Test
    fun quitarEventoConfirmadoLlamaCallbackConEseEvento() {
        var removedId: String? = null
        setPanel(
            UserPanelState.Loaded(events = listOf(event)),
            onRemoveEvent = { removedId = it.eventId }
        )

        composeRule.onNodeWithTag("remove_event_e1").performClick()
        composeRule.onNodeWithTag("confirm_remove_button").performClick()

        assertEquals("e1", removedId)
    }

    @Test
    fun quitarEventoCanceladoNoLlamaCallback() {
        var removed = false
        setPanel(
            UserPanelState.Loaded(events = listOf(event)),
            onRemoveEvent = { removed = true }
        )

        composeRule.onNodeWithTag("remove_event_e1").performClick()
        composeRule.onNodeWithText(str(R.string.common_cancel)).performClick()

        assertEquals(false, removed)
        composeRule.onNodeWithText("Fiesta de prueba").assertExists()
    }

    @Test
    fun botonAgregarEventoLlamaCallback() {
        var added = false
        setPanel(UserPanelState.Loaded(), onAddEvent = { added = true })

        composeRule.onNodeWithTag("add_event_button").performClick()

        assertTrue(added)
    }

    @Test
    fun botonRegresarLlamaCallback() {
        var back = false
        setPanel(UserPanelState.Loaded(), onBack = { back = true })

        composeRule.onNodeWithContentDescription(str(R.string.common_back)).performClick()

        assertTrue(back)
    }

    @Test
    fun errorAlQuitarMuestraMensaje() {
        setPanel(UserPanelState.Loaded(events = listOf(event)), removeFailed = true)
        composeRule.onNodeWithText(str(R.string.panel_remove_failed)).assertExists()
    }
}