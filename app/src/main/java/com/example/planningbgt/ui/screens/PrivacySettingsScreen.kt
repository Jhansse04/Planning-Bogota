package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark

data class PrivacyOption(
    val key: String,
    val label: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    currentPrivacy: String,
    onPrivacyChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedPrivacy by remember { mutableStateOf(currentPrivacy) }

    val privacyOptions = listOf(
        PrivacyOption(
            key = "public",
            label = "Todos",
            description = "Cualquier usuario puede ver tu perfil y actividad."
        ),
        PrivacyOption(
            key = "friends",
            label = "Solo amigos",
            description = "Solo tus conexiones pueden ver tu perfil."
        ),
        PrivacyOption(
            key = "private",
            label = "Nadie",
            description = "Tu perfil es completamente privado."
        )
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar"
                    )
                }
                Text(
                    text = "Configuración de Privacidad",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Visibilidad del perfil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Elige quién puede ver tu perfil y tu actividad en la plataforma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            privacyOptions.forEach { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .selectable(
                            selected = selectedPrivacy == option.key,
                            onClick = {
                                selectedPrivacy = option.key
                                onPrivacyChanged(option.key)
                            },
                            role = Role.RadioButton
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPrivacy == option.key)
                            PrimaryYellow.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPrivacy == option.key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryYellowDark
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
