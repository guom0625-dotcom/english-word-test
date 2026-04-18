package com.wordtest.app.data.repository

import com.wordtest.app.data.api.WordPair
import com.wordtest.app.data.db.WordDao
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.db.WordSessionEntity
import kotlinx.coroutines.flow.Flow

class WordRepository(private val dao: WordDao) {
    fun getAllSessions(): Flow<List<WordSessionEntity>> = dao.getAllSessions()

    fun getWordsBySession(sessionId: Long): Flow<List<WordEntity>> =
        dao.getWordsBySession(sessionId)

    suspend fun getWordsBySessionOnce(sessionId: Long): List<WordEntity> =
        dao.getWordsBySessionOnce(sessionId)

    suspend fun saveSession(name: String, words: List<WordPair>): Long {
        val sessionId = dao.insertSession(WordSessionEntity(name = name))
        val entities = words.map { pair ->
            WordEntity(
                sessionId = sessionId,
                english = pair.english,
                korean = pair.korean,
                partOfSpeech = pair.partOfSpeech,
                isSynonym = pair.isSynonym
            )
        }
        dao.insertWords(entities)
        return sessionId
    }

    suspend fun updateWord(word: WordEntity) = dao.updateWord(word)

    suspend fun deleteWord(word: WordEntity) = dao.deleteWord(word)

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteWordsBySession(sessionId)
        dao.deleteSession(sessionId)
    }

    suspend fun replaceWords(sessionId: Long, words: List<WordEntity>) {
        dao.deleteWordsBySession(sessionId)
        dao.insertWords(words.map { it.copy(sessionId = sessionId) })
    }
}
