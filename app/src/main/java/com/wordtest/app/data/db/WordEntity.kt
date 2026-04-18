package com.wordtest.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Long,
    val english: String,
    val korean: String
)
