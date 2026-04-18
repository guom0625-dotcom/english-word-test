package com.wordtest.app.domain

import com.wordtest.app.data.db.WordEntity

data class TestWord(
    val entity: WordEntity,
    var wrongCount: Int = 0,
    var correct: Boolean = false,
    var answered: Boolean = false
)

class TestEngine(words: List<WordEntity>) {
    val testWords: List<TestWord> = words.shuffled().map { TestWord(it) }
    private var currentIndex = 0

    val current: TestWord? get() = testWords.getOrNull(currentIndex)
    val isFinished: Boolean get() = currentIndex >= testWords.size
    val score: Int get() = testWords.count { it.correct }
    val total: Int get() = testWords.size
    val wrongWords: List<TestWord> get() = testWords.filter { !it.correct }

    fun onVoiceCorrect() {
        current?.apply { correct = true; answered = true }
        currentIndex++
    }

    fun onVoiceWrong() {
        current?.wrongCount = (current?.wrongCount ?: 0) + 1
    }

    fun needsMultipleChoice(): Boolean = (current?.wrongCount ?: 0) >= 2

    fun onMultipleChoiceAnswered(isCorrect: Boolean) {
        current?.apply { correct = isCorrect; answered = true }
        currentIndex++
    }

    fun generateChoices(allWords: List<WordEntity>): List<WordEntity> {
        val correct = current?.entity ?: return emptyList()
        val distractors = allWords
            .filter { it.id != correct.id }
            .shuffled()
            .take(3)
        return (distractors + correct).shuffled()
    }

    fun checkAnswer(spoken: String, expected: String): Boolean {
        val normalizedSpoken = spoken.trim().lowercase()
        val normalizedExpected = expected.trim().lowercase()
        return normalizedSpoken == normalizedExpected ||
                normalizedSpoken.contains(normalizedExpected) ||
                normalizedExpected.contains(normalizedSpoken)
    }
}
