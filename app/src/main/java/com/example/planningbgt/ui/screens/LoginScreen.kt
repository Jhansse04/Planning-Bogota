package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val authRepository = remember { com.example.planningbgt.repository.AuthRepository() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Planning Bogotá", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isRegisterMode) "Crear cuenta nueva" else "Iniciar sesión",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre completo") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Por favor completa todos los campos"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        if (isRegisterMode) {
                            val result = authRepository.register(email, password, name)
                            isLoading = false
                            result.onSuccess {
                                onRegisterSuccess()
                            }.onFailure {
                                errorMessage = it.localizedMessage ?: "Error al registrarse"
                            }
                        } else {
                            val result = authRepository.login(email, password)
                            isLoading = false
                            result.onSuccess {
                                onLoginSuccess()
                            }.onFailure {
                                errorMessage = it.localizedMessage ?: "Error de inicio de sesión"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Registrarse" else "Iniciar Sesión")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    isRegisterMode = !isRegisterMode
                    errorMessage = null
                }
            ) {
                Text(
                    if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión" 
                    else "¿No tienes cuenta? Regístrate aquí"
                )
            }
        }
    }
}
