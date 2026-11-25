package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planningbgt.model.EventFiltersData
import com.example.planningbgt.model.getTypeLabel
import com.example.planningbgt.ui.components.EventSearchBar
import com.example.planningbgt.ui.components.EventTypeBadge
import com.example.planningbgt.ui.components.FilterChipsRow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(viewModel: EventsViewModel = viewModel()) {
    var showDatePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EventSearchBar(query = viewModel.searchQuery, onQueryChange = { viewModel.onSearchChange(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.selectedDateMillis != null) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(viewModel.selectedDateMillis!!)) else "Filtrar por fecha")
            }
            if (viewModel.selectedDateMillis != null || viewModel.selectedCategory != null || viewModel.selectedEventType != null || viewModel.searchQuery.isNotBlank()) {
                OutlinedButton(onClick = { viewModel.clearFilters() }) { Icon(Icons.Filled.Clear, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Limpiar") }
            }
        }
        FilterChipsRow(title = "Categoría", options = EventFiltersData.categories, selected = viewModel.selectedCategory, onSelect = { viewModel.onCategoryChange(it) })
        FilterChipsRow(title = "Tipo", options = EventFiltersData.eventTypes, selected = viewModel.selectedEventType, onSelect = { viewModel.onEventTypeChange(it) }, labelMapper = { it.getTypeLabel() })
        if (viewModel.isLoading) { Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
        else if (viewModel.events.isEmpty()) { Text("No se encontraron eventos con esos filtros.", style = MaterialTheme.typography.bodyMedium) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(viewModel.events) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium)
                                EventTypeBadge(event.eventType)
                            }
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            Text("Categoría: ${event.category} • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(event.date.toDate())}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = viewModel.selectedDateMillis)
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { viewModel.onDateChange(datePickerState.selectedDateMillis); showDatePicker = false }) { Text("Filtrar") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }
}
