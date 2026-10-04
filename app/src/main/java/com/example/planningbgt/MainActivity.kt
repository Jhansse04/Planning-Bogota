package com.example.planningbgt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.planningbgt.repository.AuthRepository
import com.example.planningbgt.ui.screens.LoginScreen
import com.example.planningbgt.ui.screens.MainScreen
import com.example.planningbgt.ui.theme.PlanningBGTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanningBGTTheme {
                val authRepository = remember { AuthRepository() }
                var currentScreen by remember {
                    mutableStateOf(if (authRepository.currentUser != null) "main" else "login")
                }

                when (currentScreen) {
                    "login" -> {
                        LoginScreen(
                            onLoginSuccess = { currentScreen = "main" },
                            onRegisterSuccess = { currentScreen = "main" }
                        )
                    }
                    "main" -> {
                        MainScreen(
                            onLogout = {
                                authRepository.logout()
                                currentScreen = "login"
                            }
                        )
                    }
                }
            }
        }
    }
}