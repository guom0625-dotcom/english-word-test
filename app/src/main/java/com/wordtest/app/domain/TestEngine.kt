package com.wordtest.app.domain

import com.wordtest.app.data.db.WordEntity

enum class Difficulty { EASY, NORMAL, HARD, VERY_HARD }

data class TestWord(
    val entity: WordEntity,
    var wrongCount: Int = 0,
    var correct: Boolean = false,
    var answered: Boolean = false
)

class TestEngine(words: List<WordEntity>, ordered: Boolean = false, private val multipleChoiceOnly: Boolean = false, private val difficulty: Difficulty = Difficulty.NORMAL) {
    val testWords: List<TestWord> = (if (ordered) words else words.shuffled()).map { TestWord(it) }
    private var currentIndex = 0

    val current: TestWord? get() = testWords.getOrNull(currentIndex)
    val isFinished: Boolean get() = currentIndex >= testWords.size
    val score: Int get() = testWords.count { it.correct }
    val total: Int get() = testWords.size
    val questionNumber: Int get() = currentIndex + 1
    val answeredCount: Int get() = currentIndex
    val wrongWords: List<TestWord> get() = testWords.filter { !it.correct }
    val answeredWrongWords: List<TestWord> get() = testWords.filter { it.answered && !it.correct }

    fun onVoiceCorrect() {
        current?.apply { correct = true; answered = true }
        currentIndex++
    }

    fun onVoiceWrong() {
        current?.wrongCount = (current?.wrongCount ?: 0) + 1
    }

    fun needsMultipleChoice(): Boolean = multipleChoiceOnly || (current?.wrongCount ?: 0) >= 2

    fun onMultipleChoiceAnswered(isCorrect: Boolean) {
        current?.apply { correct = isCorrect; answered = true }
        currentIndex++
    }

    fun generateChoices(allWords: List<WordEntity>, reverseMode: Boolean = false): List<WordEntity> {
        val correct = current?.entity ?: return emptyList()
        val correctText = if (reverseMode) correct.korean else correct.english
        val distractors = allWords
            .filter { it.id != correct.id }
            .shuffled()
            .filter { w ->
                val text = if (reverseMode) w.korean else w.english
                text.isNotBlank() && text != correctText
            }
            .distinctBy { if (reverseMode) it.korean else it.english }
            .take(3)
        return (distractors + correct).shuffled()
    }

    fun checkAnswer(candidates: List<String>, expected: String): Boolean {
        val normalizedExpected = expected.trim().lowercase()
        return candidates.any { spoken ->
            val s = spoken.trim().lowercase()
            val allowContains = difficulty == Difficulty.EASY || difficulty == Difficulty.NORMAL
            s == normalizedExpected ||
            (allowContains && (s.contains(normalizedExpected) || normalizedExpected.contains(s))) ||
            levenshtein(s, normalizedExpected) <= allowedErrors(normalizedExpected.length)
        }
    }

    fun checkKoreanAnswer(candidates: List<String>, expected: String): Boolean {
        val tokens = expected.split(Regex("[,/·()\\s]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        val allowContains = difficulty == Difficulty.EASY || difficulty == Difficulty.NORMAL
        return candidates.any { spoken ->
            val s = spoken.trim().lowercase()
            tokens.any { token ->
                val t = token.lowercase()
                s == t ||
                (allowContains && (s.contains(t) || t.contains(s))) ||
                levenshtein(s, t) <= allowedErrors(t.length)
            }
        }
    }

    private fun allowedErrors(len: Int) = when (difficulty) {
        Difficulty.EASY -> when {
            len <= 5 -> 1
            len <= 8 -> 2
            else -> 3
        }
        Difficulty.NORMAL -> when {
            len <= 5 -> 0
            len <= 8 -> 1
            else -> 2
        }
        Difficulty.HARD -> when {
            len <= 8 -> 0
            else -> 1
        }
        Difficulty.VERY_HARD -> 0
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) { 0 } }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                       else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[a.length][b.length]
    }
}
