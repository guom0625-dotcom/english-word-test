package com.wordtest.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.UpdateChecker
import com.wordtest.app.data.UpdateInfo
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WordRepository,
    private val updateChecker: UpdateChecker
) : ViewModel() {
    val sessions = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate = _isCheckingUpdate.asStateFlow()

    // 선택된 세션의 단어 수 (enabled, total)
    private val _sessionCounts = MutableStateFlow<Pair<Int, Int>?>(null)
    val sessionCounts = _sessionCounts.asStateFlow()

    fun loadSessionCounts(sessionId: Long) {
        viewModelScope.launch {
            val words = repository.getWordsBySessionOnce(sessionId)
            _sessionCounts.value = Pair(words.count { it.isEnabled }, words.size)
        }
    }

    fun clearSessionCounts() { _sessionCounts.value = null }

    fun checkForUpdate() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateInfo.value = null
            updateChecker.checkForUpdate()
                .onSuccess { _updateInfo.value = it }
            _isCheckingUpdate.value = false
        }
    }

    fun startUpdate(downloadUrl: String) {
        viewModelScope.launch {
            _downloadProgress.value = 0
            updateChecker.downloadAndInstall(downloadUrl) { progress ->
                _downloadProgress.value = progress
            }.onFailure {
                _downloadProgress.value = null
            }
        }
    }

    fun dismissUpdate() { _updateInfo.value = null }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
