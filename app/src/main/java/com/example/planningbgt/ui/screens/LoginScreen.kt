package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.planningbgt.R
import com.example.planningbgt.repository.ProfileSaveException
import com.example.planningbgt.ui.theme.PrimaryYellowDark
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
    var wantsPro by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val authRepository = remember { com.example.planningbgt.repository.AuthRepository() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val profileSaveErrorText = stringResource(R.string.pro_register_failed_profile)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black
    )

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
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (isRegisterMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = wantsPro,
                        onValueChange = { wantsPro = it },
                        role = Role.Checkbox
                    )
            ) {
                Checkbox(
                    checked = wantsPro,
                    onCheckedChange = null, // el Row maneja el toque (accesibilidad)
                    colors = CheckboxDefaults.colors(checkedColor = PrimaryYellowDark)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pro_register_checkbox),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
                            val result = authRepository.register(
                                email = email,
                                pass = password,
                                name = name,
                                accountType = if (wantsPro) "pro" else "normal"
                            )
                            isLoading = false
                            result.onSuccess {
                                onRegisterSuccess()
                            }.onFailure {
                                errorMessage = when (it) {
                                    is ProfileSaveException -> profileSaveErrorText
                                    else -> it.localizedMessage ?: "Error al registrarse"
                                }
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
                    wantsPro = false
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
