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
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun normalizeBaseUrl(input: String): String {
        var url = input.trim().trimEnd('/')
        require(url.startsWith("https://", ignoreCase = true)) {
            "استخدم عنوان API يبدأ بـ https://"
        }
        url = when {
            url.endsWith("/chat/completions", true) -> url.removeSuffix("/chat/completions")
            url.endsWith("/models", true) -> url.removeSuffix("/models")
            else -> url
        }.trimEnd('/')
        if (url.endsWith("/openai", true)) url += "/v1"
        return url
    }

    private fun chatEndpoint(input: String): String {
        val base = normalizeBaseUrl(input)
        return if (base.endsWith("/v1", true)) "$base/chat/completions" else "$base/chat/completions"
    }

    private fun modelsEndpoint(input: String): String = "${normalizeBaseUrl(input)}/models"

    private fun errorMessage(code: Int, body: String, endpoint: String? = null): String {
        val detail = runCatching {
            val error = JSONObject(body).optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: error?.optString("code")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val providerMessage = detail ?: body.take(250).ifBlank { "لم يرسل الخادم تفاصيل إضافية." }
        val hint = when (code) {
            400 -> "راجع اسم النموذج والطلب."
            401 -> "مفتاح API غير صحيح أو منتهي الصلاحية."
            403 -> "المفتاح لا يملك صلاحية الوصول إلى هذا المورد أو النموذج."
            404 -> "العنوان أو النموذج غير موجود. ${endpoint?.let { "العنوان: $it." } ?: "تحقق من عنوان API."}"
            429 -> "تم بلوغ حد الاستخدام أو الحصة. راجع لوحة Groq."
            else -> "تحقق من إعدادات المزود ثم أعد المحاولة."
        }
        return "خطأ API ($code): $providerMessage\n$hint"
    }

    suspend fun discoverModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "ألصق مفتاح API أولًا." }
            val endpoint = modelsEndpoint(baseUrl)
            val request = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body, endpoint))
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val ids = (0 until data.length()).mapNotNull { index ->
                    data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
                }.distinct().sorted()
                if (ids.isEmpty()) throw IllegalStateException("تم الاتصال، لكن لم يُرجع المزود أي نماذج متاحة لهذا المفتاح.")
                ids
            }
        }

    suspend fun ask(baseUrl: String, apiKey: String, model: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "ألصق مفتاح API أولًا." }
            require(model.isNotBlank()) { "اختر نموذجًا من قائمة النماذج." }
            require(prompt.isNotBlank()) { "اكتب رسالة قبل الإرسال." }

            val endpoint = chatEndpoint(baseUrl)
            val cleanModel = model.trim()
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "أنت IslamGPT AI، مساعد ذكي يتحدث العربية. أجب بوضوح واختصار، وقدّم خطوات آمنة. لا تدّع تنفيذ إجراء على الهاتف ما لم تنفذه أداة فعلًا."))
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
                if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, body, "$endpoint (model: $cleanModel)"))
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("نجح الاتصال لكن لم يُرجع النموذج نصًا مفهومًا.")
            }
        }
}
