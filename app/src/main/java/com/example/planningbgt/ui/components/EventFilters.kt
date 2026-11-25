package com.example.planningbgt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planningbgt.model.getTypeLabel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField

@Composable
fun EventSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar eventos...") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Buscar"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Limpiar búsqueda"
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun FilterChipsRow(
    title: String, options: List<String>, selected: String?,
    onSelect: (String?) -> Unit, labelMapper: (String) -> String = { it }
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Todos") })
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(labelMapper(option)) }
            )
        }
    }
}

@Composable
fun EventTypeBadge(eventType: String) {
    val (icon, containerColor) = when (eventType) {
        "public" -> Icons.Filled.Public to MaterialTheme.colorScheme.primaryContainer
        "private" -> Icons.Filled.Lock to MaterialTheme.colorScheme.secondaryContainer
        "paid_promotion" -> Icons.Filled.Star to MaterialTheme.colorScheme.tertiaryContainer
        else -> Icons.Filled.Public to MaterialTheme.colorScheme.surfaceVariant
    }
    AssistChip(
        onClick = {}, label = { Text(eventType.getTypeLabel()) },
        leadingIcon = { Icon(icon, contentDescription = eventType) },
        colors = AssistChipDefaults.assistChipColors(containerColor = containerColor)
    )
}
