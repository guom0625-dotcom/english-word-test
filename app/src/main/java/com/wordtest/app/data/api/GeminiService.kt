package com.wordtest.app.data.api

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.wordtest.app.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WordPair(val english: String, val korean: String)

class GeminiService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun extractWordsFromImage(bitmap: Bitmap): Result<List<WordPair>> = runCatching {
        val prompt = """
            이 이미지에서 영단어와 한글 뜻 쌍을 모두 찾아서 JSON 배열로만 반환해줘.
            다른 설명 없이 순수한 JSON 배열만 반환해야 해.
            형식 예시: [{"english": "apple", "korean": "사과"}, {"english": "book", "korean": "책"}]
            단어 쌍이 없으면 빈 배열 []을 반환해.
        """.trimIndent()

        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )

        val text = response.text ?: return@runCatching emptyList()
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        json.decodeFromString<List<WordPair>>(cleaned)
    }
}
