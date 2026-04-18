package com.wordtest.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.db.WordSessionEntity
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WordRepository) : ViewModel() {
    val sessions = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
