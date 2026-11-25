package com.example.planningbgt.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planningbgt.model.Event
import com.example.planningbgt.repository.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EventsViewModel(private val repo: FirestoreRepository = FirestoreRepository()) : ViewModel() {
    var searchQuery by mutableStateOf(""); private set
    var selectedCategory by mutableStateOf<String?>(null); private set
    var selectedEventType by mutableStateOf<String?>(null); private set
    var selectedDateMillis by mutableStateOf<Long?>(null); private set
    var events by mutableStateOf<List<Event>>(emptyList()); private set
    var isLoading by mutableStateOf(false); private set
    private var searchJob: Job? = null
    private val viewerUid get() = FirebaseAuth.getInstance().currentUser?.uid
    init { loadEvents() }
    fun onSearchChange(q: String) { searchQuery = q; searchJob?.cancel(); searchJob = viewModelScope.launch { delay(300); loadEvents() } }
    fun onCategoryChange(c: String?) { selectedCategory = c; loadEvents() }
    fun onEventTypeChange(t: String?) { selectedEventType = t; loadEvents() }
    fun onDateChange(d: Long?) { selectedDateMillis = d; loadEvents() }
    fun clearFilters() { selectedCategory = null; selectedEventType = null; selectedDateMillis = null; searchQuery = ""; loadEvents() }
    fun loadEvents() {
        viewModelScope.launch {
            isLoading = true
            events = repo.getFilteredEvents(viewerUid = viewerUid, queryText = searchQuery, category = selectedCategory, eventType = selectedEventType, dateMillis = selectedDateMillis)
            isLoading = false
        }
    }
}
