package com.wordtest.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM word_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<WordSessionEntity>>

    @Query("SELECT * FROM words WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getWordsBySession(sessionId: Long): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getWordsBySessionOnce(sessionId: Long): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WordSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Update
    suspend fun updateWord(word: WordEntity)

    @Delete
    suspend fun deleteWord(word: WordEntity)

    @Query("DELETE FROM words WHERE sessionId = :sessionId")
    suspend fun deleteWordsBySession(sessionId: Long)

    @Query("DELETE FROM word_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("UPDATE word_sessions SET name = :name WHERE id = :sessionId")
    suspend fun updateSessionName(sessionId: Long, name: String)

    @Query("SELECT * FROM word_sessions WHERE id = :sessionId")
    fun getSession(sessionId: Long): Flow<WordSessionEntity?>

    @Query("SELECT COUNT(*) FROM words WHERE sessionId = :sessionId")
    suspend fun getWordCount(sessionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: WordEntity): Long

    @Query("SELECT COUNT(*) FROM words WHERE sessionId = :sessionId AND lower(english) = lower(:english)")
    suspend fun existsByEnglish(sessionId: Long, english: String): Int

    @Query("UPDATE words SET correctCount = correctCount + :correct, wrongCount = wrongCount + :wrong WHERE id = :id")
    suspend fun incrementStats(id: Int, correct: Int, wrong: Int)
}
