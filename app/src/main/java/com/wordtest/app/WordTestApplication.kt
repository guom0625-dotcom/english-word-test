package com.wordtest.app

import android.app.Application
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.db.AppDatabase
import com.wordtest.app.data.repository.WordRepository

class WordTestApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { WordRepository(database.wordDao()) }
    val geminiService by lazy { GeminiService() }
}
