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
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val modelFallbacks = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash-lite"
    )
    private var lastSuccessfulModelIndex = 0

    private fun streamUrl(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=${apiKeyStore.getApiKey()}"

    private val prompt = """
        이 이미지는 영단어 학습 교재야. 영단어와 한글 뜻을 추출해서 JSON 배열로만 반환해줘.
        다른 설명 없이 순수한 JSON 배열만 반환해야 해.

        규칙:
        1. 번호(0001, 0002 등)와 체크박스(□) 기호는 무시해.
        2. 품사(v., n., a., ad. 등)는 partOfSpeech 필드에 저장하고, korean 필드에서는 제거해.
        3. 같은 영단어가 여러 품사(v., n. 등)로 여러 번 나타나면, 반드시 하나로 합쳐줘.
           - korean: 뜻을 " / " 로 구분해서 합쳐. 예: "제공하다, 공급하다 / 조항, 규정"
           - partOfSpeech: 품사도 합쳐. 예: "v./n."
        4. = 기호가 붙은 유의어도 포함해. isSynonym: true로 설정해.
        5. <-> 기호는 "앞 단어의 반대어"를 나타내. <-> 뒤에 오는 단어만 isAntonym: true로 설정해.
           <-> 앞에 있는 단어는 일반 단어이므로 isAntonym: false를 유지해.
           예시: "provide <-> deprive" → provide는 isAntonym: false, deprive는 isAntonym: true
        6. isSynonym, isAntonym 기본값은 false야.

        형식 예시:
        [
          {"english": "provide", "korean": "제공하다, 공급하다", "partOfSpeech": "v.", "isSynonym": false, "isAntonym": false},
          {"english": "employ", "korean": "고용하다 / 고용", "partOfSpeech": "v./n.", "isSynonym": false, "isAntonym": false}
        ]

        단어 쌍이 없으면 빈 배열 []을 반환해.
    """.trimIndent()

    // 한 페이지 기준 예상 응답 길이 (단어 30개 * 평균 100자)
    private val estimatedResponseChars = 3000

    suspend fun extractWordsFromImage(
        bitmap: Bitmap,
        onProgress: (Float) -> Unit = {},
        onModelSelected: (String) -> Unit = {},
        onStatus: (String) -> Unit = {}
    ): Result<List<WordPair>> = withContext(Dispatchers.IO) {
        val imageBase64 = bitmapToBase64(bitmap)
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

        val orderedModels = modelFallbacks.indices.map { modelFallbacks[(lastSuccessfulModelIndex + it) % modelFallbacks.size] }
        var lastError: Exception = Exception("알 수 없는 오류")
        for (model in orderedModels) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(streamUrl(model))
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                onStatus("$model 연결 중...")
                val response = client.newCall(request).execute()
                Log.d("GeminiService", "[$model] Response code: ${response.code}")

                if (response.code == 503 || response.code == 429) {
                    response.body?.close()
                    onStatus("$model 사용 불가, 다음 모델 시도 중...")
                    throw Exception("모델 사용 불가 (${response.code}): $model")
                }
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw Exception("API 오류 ${response.code}: $errBody")
                }

                onModelSelected(model)
                val accumulated = StringBuilder()
                val source = response.body?.source() ?: throw Exception("응답 없음")
                onProgress(0.02f)
                onStatus("$model · 분석 중...")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val dataStr = line.removePrefix("data:").trim()
                    if (dataStr == "[DONE]") break
                    runCatching {
                        val chunk = Json.parseToJsonElement(dataStr).jsonObject
                        val text = chunk["candidates"]
                            ?.jsonArray?.getOrNull(0)
                            ?.jsonObject?.get("content")
                            ?.jsonObject?.get("parts")
                            ?.jsonArray?.getOrNull(0)
                            ?.jsonObject?.get("text")
                            ?.jsonPrimitive?.content ?: ""
                        accumulated.append(text)
                        val progress = (accumulated.length.toFloat() / estimatedResponseChars).coerceIn(0.02f, 0.95f)
                        onProgress(progress)
                        onStatus("$model · 분석 중... ${(progress * 100).toInt()}%")
                    }
                }

                onProgress(1f)
                val fullText = accumulated.toString().trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                json.decodeFromString<List<WordPair>>(fullText)
            }

            if (result.isSuccess) {
                lastSuccessfulModelIndex = modelFallbacks.indexOf(model)
                return@withContext result
            }
            lastError = result.exceptionOrNull() as? Exception ?: lastError
            Log.w("GeminiService", "[$model] 실패, 다음 모델 시도: ${lastError.message}")
        }

        Log.e("GeminiService", "모든 모델 실패: ${lastError.message}")
        Result.failure(lastError)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
