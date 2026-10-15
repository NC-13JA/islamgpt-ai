package com.islamgpt.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IslamGptScreen(this) }
    }
}

private data class ChatLine(val fromUser: Boolean, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IslamGptScreen(activity: Activity) {
    val prefs = remember { activity.getSharedPreferences("islamgpt", Context.MODE_PRIVATE) }
    var baseUrl by remember { mutableStateOf(prefs.getString("baseUrl", "https://api.groq.com/openai/v1") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("apiKey", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "llama-3.3-70b-versatile") ?: "") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf(ChatLine(false, "مرحبًا! أنا IslamGPT AI. أضف إعدادات API ثم اكتب أمرًا أو استخدم الإملاء الصوتي.")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) prompt = spoken
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech(speechLauncher::launch) else status = "لم يتم منح إذن الميكروفون."
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(title = { Text("IslamGPT AI") }, actions = {
                Text(if (serviceOn) "● يعمل" else "○ متوقف", modifier = Modifier.padding(end = 12.dp))
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("إعدادات الذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("عنوان API (Groq: https://api.groq.com/openai/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("مفتاح API") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("اسم النموذج (مثال: llama-3.3-70b-versatile)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        prefs.edit().putString("baseUrl", baseUrl.trim()).putString("apiKey", apiKey.trim()).putString("model", model.trim()).apply()
                        status = "حُفظت الإعدادات على هذا الجهاز."
                    }) { Text("حفظ الإعدادات") }
                    Button(onClick = {
                        val intent = Intent(activity, AssistantForegroundService::class.java)
                        if (serviceOn) {
                            intent.action = AssistantForegroundService.ACTION_STOP
                            activity.startService(intent)
                            serviceOn = false
                        } else {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            ContextCompat.startForegroundService(activity, intent)
                            serviceOn = true
                        }
                    }) { Text(if (serviceOn) "إيقاف الخدمة" else "تشغيل المساعد") }
                }
                TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("إعدادات إمكانية الوصول")
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.secondary)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("المحادثة", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lines) { line ->
                        Surface(
                            color = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text((if (line.fromUser) "أنت: " else "IslamGPT: ") + line.text, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.weight(1f), label = { Text("اكتب أمرك هنا") })
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else startSpeech(speechLauncher::launch)
                    }) { Text("صوت") }
                }
                Button(
                    enabled = !busy && prompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val question = prompt.trim()
                        prompt = ""
                        lines.add(ChatLine(true, question))
                        busy = true
                        scope.launch {
                            try {
                                lines.add(ChatLine(false, ApiClient.ask(baseUrl, apiKey, model, question)))
                            } catch (e: Exception) {
                                lines.add(ChatLine(false, "تعذر إكمال الطلب: ${e.message ?: "خطأ غير معروف"}"))
                            } finally {
                                busy = false
                                listState.animateScrollToItem(lines.lastIndex.coerceAtLeast(0))
                            }
                        }
                    }
                ) { Text(if (busy) "جارٍ الاتصال..." else "إرسال") }
            }
        }
    }
}

private fun startSpeech(launch: (Intent) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale("ar"))
        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى IslamGPT AI")
    }
    launch(intent)
}
