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

    @Query("SELECT COUNT(*) FROM words WHERE sessionId = :sessionId")
    suspend fun getWordCount(sessionId: Long): Int
}
