package com.wordtest.app.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository
import com.wordtest.app.domain.TestEngine
import com.wordtest.app.domain.TestWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TestUiState {
    object Loading : TestUiState()
    data class Voice(val word: TestWord, val wrongCount: Int) : TestUiState()
    data class MultipleChoice(val word: TestWord, val choices: List<WordEntity>) : TestUiState()
    data class Finished(val score: Int, val total: Int) : TestUiState()
}

class TestViewModel(
    private val sessionId: Long,
    private val repository: WordRepository,
    private val ordered: Boolean = false,
    private val multipleChoiceOnly: Boolean = false,
    val reverseMode: Boolean = false
) : ViewModel() {
    private val _uiState = MutableStateFlow<TestUiState>(TestUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private lateinit var engine: TestEngine
    private var allWords: List<WordEntity> = emptyList()

    init {
        viewModelScope.launch {
            allWords = repository.getWordsBySessionOnce(sessionId)
            val testWords = allWords.filter { it.isEnabled }
            engine = if (testWords.isEmpty()) TestEngine(allWords, ordered, multipleChoiceOnly)
                     else TestEngine(testWords, ordered, multipleChoiceOnly)
            showCurrent()
        }
    }

    private fun showCurrent() {
        if (engine.isFinished) {
            _uiState.value = TestUiState.Finished(engine.score, engine.total)
            return
        }
        val word = engine.current ?: return
        if (engine.needsMultipleChoice()) {
            _uiState.value = TestUiState.MultipleChoice(word, engine.generateChoices(allWords))
        } else {
            _uiState.value = TestUiState.Voice(word, word.wrongCount)
        }
    }

    fun onAnswerSubmitted(candidates: List<String>) {
        val current = engine.current ?: return
        val correct = if (reverseMode) {
            engine.checkKoreanAnswer(candidates, current.entity.korean)
        } else {
            engine.checkAnswer(candidates, current.entity.english)
        }
        if (correct) engine.onVoiceCorrect() else engine.onVoiceWrong()
        showCurrent()
    }

    fun onMultipleChoiceSelected(selected: WordEntity) {
        val isCorrect = selected.id == engine.current?.entity?.id
        engine.onMultipleChoiceAnswered(isCorrect)
        showCurrent()
    }

    fun getWrongWords() = engine.wrongWords
}
