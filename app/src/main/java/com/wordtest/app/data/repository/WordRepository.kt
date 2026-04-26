package com.wordtest.app.data.repository

import com.wordtest.app.data.api.WordPair
import com.wordtest.app.data.db.WordDao
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.db.WordSessionEntity
import kotlinx.coroutines.flow.Flow

class WordRepository(private val dao: WordDao) {
    fun getAllSessions(): Flow<List<WordSessionEntity>> = dao.getAllSessions()
    fun getSession(sessionId: Long): Flow<WordSessionEntity?> = dao.getSession(sessionId)

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
                isSynonym = pair.isSynonym,
                isAntonym = pair.isAntonym
            )
        }
        dao.insertWords(entities)
        return sessionId
    }

    // 중복 없으면 추가, 있으면 무시. true = 추가됨
    suspend fun addWordIfNew(word: WordEntity): Boolean {
        if (dao.existsByEnglish(word.sessionId, word.english) > 0) return false
        dao.insertWord(word)
        return true
    }

    suspend fun updateWord(word: WordEntity) = dao.updateWord(word)
    suspend fun deleteWord(word: WordEntity) = dao.deleteWord(word)
    suspend fun incrementStats(id: Int, isCorrect: Boolean) =
        dao.incrementStats(id, if (isCorrect) 1 else 0, if (isCorrect) 0 else 1)
    suspend fun renameSession(sessionId: Long, name: String) = dao.updateSessionName(sessionId, name)

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteWordsBySession(sessionId)
        dao.deleteSession(sessionId)
    }
}
