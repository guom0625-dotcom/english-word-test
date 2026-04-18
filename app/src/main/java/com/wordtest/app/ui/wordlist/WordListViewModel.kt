package com.wordtest.app.ui.wordlist

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordListViewModel(
    private val sessionId: Long,
    private val repository: WordRepository,
    private val geminiService: GeminiService
) : ViewModel() {
    val sessionName = repository.getSession(sessionId)
        .map { it?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val words = repository.getWordsBySession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _imageError = MutableStateFlow<String?>(null)
    val imageError = _imageError.asStateFlow()

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

    fun renameSession(name: String) {
        viewModelScope.launch { repository.renameSession(sessionId, name) }
    }

    fun addWord(english: String, korean: String, partOfSpeech: String = "") {
        viewModelScope.launch {
            repository.addWordIfNew(
                WordEntity(sessionId = sessionId, english = english, korean = korean, partOfSpeech = partOfSpeech)
            )
        }
    }

    fun addWordsFromImages(bitmaps: List<Bitmap>) {
        viewModelScope.launch {
            _isProcessing.value = true
            var skipped = 0
            for (bitmap in bitmaps) {
                geminiService.extractWordsFromImage(bitmap)
                    .onSuccess { pairs ->
                        pairs.forEach { pair ->
                            val added = repository.addWordIfNew(
                                WordEntity(
                                    sessionId = sessionId,
                                    english = pair.english,
                                    korean = pair.korean,
                                    partOfSpeech = pair.partOfSpeech,
                                    isSynonym = pair.isSynonym,
                                    isAntonym = pair.isAntonym
                                )
                            )
                            if (!added) skipped++
                        }
                    }
                    .onFailure { _imageError.value = "이미지 처리 실패: ${it.message}" }
            }
            _isProcessing.value = false
            if (skipped > 0) _imageError.value = "중복 단어 ${skipped}개는 건너뛰었습니다."
        }
    }

    fun clearImageError() { _imageError.value = null }
}
