package com.wordtest.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wordtest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class UpdateInfo(
    val latestVersionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()
    private val repo = BuildConfig.GITHUB_REPO

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching null

            val body = response.body?.string() ?: return@runCatching null
            val json = Json.parseToJsonElement(body).jsonObject

            // tag_name: "v{run_number}" → versionCode = run_number
            val tagName = json["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
            val versionName = json["name"]?.jsonPrimitive?.content ?: tagName
            val latestCode = tagName.removePrefix("v").toIntOrNull() ?: return@runCatching null

            if (latestCode <= BuildConfig.VERSION_CODE) return@runCatching null

            // assets 배열에서 APK 다운로드 URL 추출
            val downloadUrl = json["assets"]?.jsonArray
                ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content
                ?: return@runCatching null

            UpdateInfo(latestCode, versionName, downloadUrl)
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String, onProgress: (Int) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body ?: throw Exception("다운로드 실패")

                val apkFile = File(context.cacheDir, "update.apk")
                val contentLength = body.contentLength()
                var bytesRead = 0L

                apkFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                onProgress((bytesRead * 100 / contentLength).toInt())
                            }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
}
