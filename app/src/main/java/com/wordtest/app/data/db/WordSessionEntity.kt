package com.wordtest.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_sessions")
data class WordSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
