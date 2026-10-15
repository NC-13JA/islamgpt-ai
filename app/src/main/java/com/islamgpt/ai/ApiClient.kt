package com.islamgpt.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun ask(baseUrl: String, apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(baseUrl.startsWith("https://")) { "استخدم عنوان API يبدأ بـ https://" }
            require(apiKey.isNotBlank()) { "أدخل مفتاح API أولًا." }
            require(model.isNotBlank()) { "أدخل اسم النموذج." }
            val url = baseUrl.trimEnd('/')
            val endpoint = if (url.endsWith("/chat/completions")) url else "$url/chat/completions"
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "أنت IslamGPT AI، مساعد أندرويد يتحدث العربية. اقترح خطوات آمنة وواضحة. لا تدّع تنفيذ إجراء على الهاتف ما لم تنفذه أداة فعلًا."))
                .put(JSONObject().put("role", "user").put("content", prompt))
            val payload = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.4)
            val request = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonType)).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IllegalStateException("خطأ API (${response.code}): ${detail?.takeIf { it.isNotBlank() } ?: body.take(300)}")
                }
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("لم يُرجع النموذج نصًا مفهومًا.")
            }
        }
}
