package com.wordtest.app

import android.app.Application
import com.wordtest.app.data.ApiKeyStore
import com.wordtest.app.data.UpdateChecker
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.db.AppDatabase
import com.wordtest.app.data.repository.WordRepository

class WordTestApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { WordRepository(database.wordDao()) }
    val apiKeyStore by lazy { ApiKeyStore(this) }
    val geminiService by lazy { GeminiService(apiKeyStore) }
    val updateChecker by lazy { UpdateChecker(this) }
}
