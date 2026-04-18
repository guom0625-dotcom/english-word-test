package com.wordtest.app.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.wordtest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class WordPair(val english: String, val korean: String)

class GeminiService {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiUrl =
        "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

    suspend fun extractWordsFromImage(bitmap: Bitmap): Result<List<WordPair>> = withContext(Dispatchers.IO) {
        runCatching {
            val imageBase64 = bitmapToBase64(bitmap)

            val prompt = "이 이미지에서 영단어와 한글 뜻 쌍을 모두 찾아서 JSON 배열로만 반환해줘. " +
                    "다른 설명 없이 순수한 JSON 배열만 반환해야 해. " +
                    "형식 예시: [{\"english\": \"apple\", \"korean\": \"사과\"}] " +
                    "단어 쌍이 없으면 빈 배열 []을 반환해."

            val requestBody = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", imageBase64)
                                }
                            }
                            addJsonObject { put("text", prompt) }
                        }
                    }
                }
            }.toString()

            Log.d("GeminiService", "Calling Gemini v1 API...")

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d("GeminiService", "Response code: ${response.code}")
            Log.d("GeminiService", "Response: $responseBody")

            if (!response.isSuccessful) {
                throw Exception("API 오류 ${response.code}: $responseBody")
            }

            val jsonResponse = Json.parseToJsonElement(responseBody).jsonObject
            val text = jsonResponse["candidates"]
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content ?: return@runCatching emptyList()

            Log.d("GeminiService", "Extracted text: $text")

            val cleaned = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            json.decodeFromString<List<WordPair>>(cleaned)
        }.onFailure {
            Log.e("GeminiService", "Error: ${it.javaClass.simpleName} - ${it.message}")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
