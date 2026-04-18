package com.wordtest.app.ui.wordlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordListViewModel(
    private val sessionId: Long,
    private val repository: WordRepository
) : ViewModel() {
    val words = repository.getWordsBySession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateWord(word: WordEntity) {
        viewModelScope.launch { repository.updateWord(word) }
    }

    fun deleteWord(word: WordEntity) {
        viewModelScope.launch { repository.deleteWord(word) }
    }

    fun toggleEnabled(word: WordEntity) {
        viewModelScope.launch { repository.updateWord(word.copy(isEnabled = !word.isEnabled)) }
    }

    fun setSynonymsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            words.value.filter { it.isSynonym }
                .forEach { repository.updateWord(it.copy(isEnabled = enabled)) }
        }
    }

    fun setAntonymsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            words.value.filter { it.isAntonym }
                .forEach { repository.updateWord(it.copy(isEnabled = enabled)) }
        }
    }

    fun toggleAll(enable: Boolean, includeSynonyms: Boolean, includeAntonyms: Boolean) {
        viewModelScope.launch {
            words.value.forEach { word ->
                val inScope = when {
                    word.isSynonym -> includeSynonyms
                    word.isAntonym -> includeAntonyms
                    else -> true
                }
                if (inScope) repository.updateWord(word.copy(isEnabled = enable))
            }
        }
    }

    fun addWord(english: String, korean: String, partOfSpeech: String = "") {
        viewModelScope.launch {
            repository.updateWord(
                WordEntity(sessionId = sessionId, english = english, korean = korean, partOfSpeech = partOfSpeech)
            )
        }
    }
}
