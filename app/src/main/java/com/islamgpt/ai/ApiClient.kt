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

    /**
     * Accepts either a Groq base URL or the full chat-completions URL.
     * Also repairs a common mistake where the user pastes the /models URL.
     */
    private fun normalizeChatEndpoint(baseUrl: String): String {
        val configured = baseUrl.trim().trimEnd('/')
        require(configured.startsWith("https://", ignoreCase = true)) {
            "استخدم عنوان API يبدأ بـ https://"
        }

        return when {
            configured.endsWith("/chat/completions", ignoreCase = true) -> configured
            configured.endsWith("/models", ignoreCase = true) ->
                configured.removeSuffix("/models") + "/chat/completions"
            configured.endsWith("/v1", ignoreCase = true) ->
                "$configured/chat/completions"
            configured.endsWith("/openai", ignoreCase = true) ->
                "$configured/v1/chat/completions"
            else -> "$configured/chat/completions"
        }
    }

    suspend fun ask(baseUrl: String, apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "أدخل مفتاح API أولًا." }
            require(model.isNotBlank()) { "أدخل اسم النموذج." }
            require(prompt.isNotBlank()) { "اكتب رسالة قبل الإرسال." }

            val endpoint = normalizeChatEndpoint(baseUrl)
            val cleanModel = model.trim()
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "أنت IslamGPT AI، مساعد أندرويد يتحدث العربية. اقترح خطوات آمنة وواضحة. لا تدّع تنفيذ إجراء على الهاتف ما لم تنفذه أداة فعلًا."))
                .put(JSONObject().put("role", "user").put("content", prompt.trim()))
            val payload = JSONObject()
                .put("model", cleanModel)
                .put("messages", messages)
                .put("temperature", 0.4)
            val request = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonType))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        val error = JSONObject(body).optJSONObject("error")
                        error?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: error?.optString("code")?.takeIf { it.isNotBlank() }
                    }.getOrNull()
                    val providerMessage = detail ?: body.take(300).ifBlank { "لا توجد تفاصيل من الخادم." }
                    val hint = when (response.code) {
                        401 -> "تحقق من صحة مفتاح Groq."
                        403 -> "تحقق من صلاحيات المفتاح وإمكانية الوصول إلى النموذج."
                        404 -> "تحقق من عنوان API ومعرّف النموذج. العنوان المستخدم: $endpoint، النموذج: $cleanModel."
                        429 -> "تم بلوغ حد الاستخدام أو الحصة؛ راجع حدود حساب Groq."
                        else -> "راجع إعدادات API ثم أعد المحاولة."
                    }
                    throw IllegalStateException("خطأ API (${response.code}): $providerMessage\n$hint")
                }

                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("نجح الاتصال لكن لم يُرجع النموذج نصًا مفهومًا.")
            }
        }
}
