package com.example.planningbgt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.planningbgt.repository.AuthRepository
import com.example.planningbgt.ui.screens.LoginScreen
import com.example.planningbgt.ui.screens.MapScreen
import com.example.planningbgt.ui.theme.PlanningBGTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanningBGTTheme {
                val authRepository = remember { AuthRepository() }
                // Start with Map if user is already logged in, otherwise Login
                var currentScreen by remember { 
                    mutableStateOf(if (authRepository.currentUser != null) "map" else "login") 
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            "login" -> {
                                LoginScreen(
                                    onLoginSuccess = { currentScreen = "map" },
                                    onRegisterSuccess = { currentScreen = "map" }
                                )
                            }
                            "map" -> {
                                MapScreen(
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
    }
}