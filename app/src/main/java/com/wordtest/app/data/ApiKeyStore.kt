package com.wordtest.app.data

import android.content.Context

class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getApiKey(): String = prefs.getString("gemini_api_key", "") ?: ""

    fun saveApiKey(key: String) = prefs.edit().putString("gemini_api_key", key).apply()

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()
}
