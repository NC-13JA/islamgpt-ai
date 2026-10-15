package com.islamgpt.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val downloadUrl: String
)

object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/NC-13JA/islamgpt-ai/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("تعذر فحص التحديثات من GitHub (HTTP ${response.code}).")
            }
            val release = JSONObject(body)
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) return@withContext null
            val tag = release.optString("tag_name").removePrefix("v")
            val assetList = release.optJSONArray("assets")
            var apkUrl: String? = null
            if (assetList != null) {
                for (i in 0 until assetList.length()) {
                    val asset = assetList.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true) && asset.optString("browser_download_url").startsWith("https://")) {
                        apkUrl = asset.optString("browser_download_url")
                        if (name == "islamgpt-ai-release.apk") break
                    }
                }
            }
            if (apkUrl == null) throw IllegalStateException("وجدت إصدار GitHub لكن لم أجد ملف APK مرفقًا به.")
            if (compareVersions(tag, currentVersion) <= 0) return@withContext null
            AppUpdate(
                tagName = "v$tag",
                versionName = tag,
                title = release.optString("name").ifBlank { "IslamGPT AI $tag" },
                notes = release.optString("body"),
                downloadUrl = apkUrl
            )
        }
    }

    suspend fun downloadApk(activity: Activity, update: AppUpdate): File = withContext(Dispatchers.IO) {
        val directory = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "islamgpt-ai-release.apk")
        val request = Request.Builder().url(update.downloadUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("فشل تنزيل التحديث (HTTP ${response.code}).")
            val body = response.body ?: throw IllegalStateException("ملف التحديث فارغ.")
            if (body.contentLength() == 0L) throw IllegalStateException("ملف التحديث فارغ.")
            body.byteStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        if (!target.exists() || target.length() < 1024) {
            target.delete()
            throw IllegalStateException("ملف APK الذي تم تنزيله غير صالح أو غير مكتمل.")
        }
        target
    }

    fun installApk(activity: Activity, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return true
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
