package com.wordtest.app.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.wordtest.app.data.ApiKeyStore
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
data class WordPair(
    val english: String,
    val korean: String,
    val partOfSpeech: String = "",
    val isSynonym: Boolean = false,
    val isAntonym: Boolean = false
)

class GeminiService(private val apiKeyStore: ApiKeyStore) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiUrl get() =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKeyStore.getApiKey()}"

    suspend fun extractWordsFromImage(
        bitmap: Bitmap
    ): Result<List<WordPair>> = withContext(Dispatchers.IO) {
        runCatching {
            val imageBase64 = bitmapToBase64(bitmap)

            val prompt = """
                이 이미지는 영단어 학습 교재야. 영단어와 한글 뜻을 추출해서 JSON 배열로만 반환해줘.
                다른 설명 없이 순수한 JSON 배열만 반환해야 해.

                규칙:
                1. 번호(0001, 0002 등)와 체크박스(□) 기호는 무시해.
                2. 품사(v., n., a., ad. 등)는 partOfSpeech 필드에 저장하고, korean 필드에서는 제거해.
                3. 같은 영단어가 여러 품사(v., n. 등)로 여러 번 나타나면, 반드시 하나로 합쳐줘.
                   - korean: 뜻을 " / " 로 구분해서 합쳐. 예: "제공하다, 공급하다 / 조항, 규정"
                   - partOfSpeech: 품사도 합쳐. 예: "v./n."
                4. = 기호가 붙은 유의어도 포함해. isSynonym: true로 설정해.
                5. <-> 기호가 붙은 반대어도 포함해. isAntonym: true로 설정해.
                6. isSynonym, isAntonym 기본값은 false야.

                형식 예시:
                [
                  {"english": "provide", "korean": "제공하다, 공급하다", "partOfSpeech": "v.", "isSynonym": false, "isAntonym": false},
                  {"english": "employ", "korean": "고용하다 / 고용", "partOfSpeech": "v./n.", "isSynonym": false, "isAntonym": false}
                ]

                단어 쌍이 없으면 빈 배열 []을 반환해.
            """.trimIndent()

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

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d("GeminiService", "Response code: ${response.code}")

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
