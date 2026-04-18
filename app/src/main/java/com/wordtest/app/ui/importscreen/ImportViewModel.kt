package com.wordtest.app.ui.importscreen

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Processing : ImportUiState()
    data class Error(val message: String) : ImportUiState()
    data class Done(val sessionId: Long) : ImportUiState()
}

class ImportViewModel(
    private val geminiService: GeminiService,
    private val repository: WordRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState = _uiState.asStateFlow()

    val selectedImages = MutableStateFlow<List<Bitmap>>(emptyList())
    val includeSynonyms = MutableStateFlow(false)
    val includeAntonyms = MutableStateFlow(false)

    fun addImage(bitmap: Bitmap) { selectedImages.value = selectedImages.value + bitmap }
    fun removeImage(index: Int) {
        selectedImages.value = selectedImages.value.toMutableList().also { it.removeAt(index) }
    }
    fun toggleSynonyms(value: Boolean) { includeSynonyms.value = value }
    fun toggleAntonyms(value: Boolean) { includeAntonyms.value = value }

    fun processImages(sessionName: String) {
        if (selectedImages.value.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = ImportUiState.Processing
            val allWords = mutableListOf<com.wordtest.app.data.api.WordPair>()
            for (bitmap in selectedImages.value) {
                geminiService.extractWordsFromImage(bitmap, includeSynonyms.value, includeAntonyms.value)
                    .onSuccess { allWords.addAll(it) }
                    .onFailure {
                        _uiState.value = ImportUiState.Error("이미지 처리 실패: ${it.message}")
                        return@launch
                    }
            }
            if (allWords.isEmpty()) {
                _uiState.value = ImportUiState.Error("단어를 찾지 못했습니다.")
                return@launch
            }
            val sessionId = repository.saveSession(sessionName, allWords)
            _uiState.value = ImportUiState.Done(sessionId)
        }
    }

    fun resetError() { _uiState.value = ImportUiState.Idle }
}
