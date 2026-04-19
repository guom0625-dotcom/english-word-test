package com.wordtest.app.domain

import com.wordtest.app.data.db.WordEntity

data class TestWord(
    val entity: WordEntity,
    var wrongCount: Int = 0,
    var correct: Boolean = false,
    var answered: Boolean = false
)

class TestEngine(words: List<WordEntity>, ordered: Boolean = false, private val multipleChoiceOnly: Boolean = false) {
    val testWords: List<TestWord> = (if (ordered) words else words.shuffled()).map { TestWord(it) }
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

    fun needsMultipleChoice(): Boolean = multipleChoiceOnly || (current?.wrongCount ?: 0) >= 2

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

    fun checkAnswer(candidates: List<String>, expected: String): Boolean {
        val normalizedExpected = expected.trim().lowercase()
        return candidates.any { spoken ->
            val s = spoken.trim().lowercase()
            s == normalizedExpected ||
            s.contains(normalizedExpected) ||
            normalizedExpected.contains(s) ||
            levenshtein(s, normalizedExpected) <= allowedErrors(normalizedExpected.length)
        }
    }

    fun checkKoreanAnswer(candidates: List<String>, expected: String): Boolean {
        val tokens = expected.split(Regex("[,/·()\\s]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
        return candidates.any { spoken ->
            val s = spoken.trim().lowercase()
            tokens.any { token ->
                val t = token.lowercase()
                s == t || s.contains(t) || t.contains(s) ||
                levenshtein(s, t) <= allowedErrors(t.length)
            }
        }
    }

    private fun allowedErrors(len: Int) = when {
        len <= 5 -> 0
        len <= 8 -> 1
        else -> 2
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
