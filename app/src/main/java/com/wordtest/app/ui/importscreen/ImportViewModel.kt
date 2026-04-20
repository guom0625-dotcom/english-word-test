package com.wordtest.app.ui.importscreen

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.delay
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

    // Triple: (현재 이미지 번호, 전체, 현재 이미지 진행률 0~1f)
    private val _progress = MutableStateFlow<Triple<Int, Int, Float>?>(null)
    val progress = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    val selectedImages = MutableStateFlow<List<Bitmap>>(emptyList())

    fun addImage(bitmap: Bitmap) { selectedImages.value = selectedImages.value + bitmap }
    fun removeImage(index: Int) {
        selectedImages.value = selectedImages.value.toMutableList().also { it.removeAt(index) }
    }

    fun processImages(sessionName: String) {
        if (selectedImages.value.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = ImportUiState.Processing
            val total = selectedImages.value.size
            val allWords = mutableListOf<com.wordtest.app.data.api.WordPair>()
            for ((index, bitmap) in selectedImages.value.withIndex()) {
                _progress.value = Triple(index + 1, total, 0f)
                geminiService.extractWordsFromImage(
                    bitmap,
                    onProgress = { p -> _progress.value = Triple(index + 1, total, p) },
                    onModelSelected = { model -> _statusMessage.value = model },
                    onStatus = { status -> _statusMessage.value = status }
                )
                    .onSuccess {
                        allWords.addAll(it)
                        if (index < total - 1) {
                            _statusMessage.value = "다음 이미지 준비 중..."
                            delay(2000)
                        }
                    }
                    .onFailure {
                        _uiState.value = ImportUiState.Error("이미지 처리 실패: ${it.message}")
                        _progress.value = null
                        _statusMessage.value = null
                        return@launch
                    }
            }
            _progress.value = null
            _statusMessage.value = null
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
