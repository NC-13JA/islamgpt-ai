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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val GROQ_URL = "https://api.groq.com/openai/v1"
private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

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
    var baseUrl by remember { mutableStateOf(prefs.getString("baseUrl", GROQ_URL) ?: GROQ_URL) }
    var apiKey by remember { mutableStateOf(prefs.getString("apiKey", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL) }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    var startupLoading by remember { mutableStateOf(true) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var modelsExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(true) }
    val lines = remember { mutableStateListOf(ChatLine(false, "أهلًا بك! أنا IslamGPT AI. الصق مفتاح Groq ثم اكتشف النماذج المتاحة للبدء.")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var pendingClickTarget by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdate?>(null) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1050)
        startupLoading = false
        runCatching {
            val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.1.0"
            UpdateManager.checkForUpdate(current)
        }.onSuccess { found ->
            updateInfo = found
            if (found != null) status = "يتوفر تحديث جديد: ${found.versionName}"
        }
    }

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

    val colors = darkColorScheme(
        primary = Color(0xFF7C9CFF),
        secondary = Color(0xFF6EE7C8),
        background = Color(0xFF0A1020),
        surface = Color(0xFF141D31),
        surfaceVariant = Color(0xFF202B42),
        primaryContainer = Color(0xFF26385E),
        onPrimary = Color(0xFF081124),
        onBackground = Color(0xFFF2F5FF),
        onSurface = Color(0xFFF2F5FF),
        onSurfaceVariant = Color(0xFFD0D8EC)
    )

    MaterialTheme(colorScheme = colors) {
        pendingClickTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingClickTarget = null },
                title = { Text("تأكيد تنفيذ الأمر") },
                text = { Text("هل تريد النقر على العنصر الظاهر الذي يحتوي على النص «$target» في التطبيق المفتوح؟") },
                confirmButton = {
                    TextButton(onClick = {
                        val success = AssistantAccessibilityService.instance?.clickVisibleText(target) == true
                        lines.add(ChatLine(false, if (success) "تم تنفيذ النقر على «$target»." else "تعذر النقر. تأكد من تفعيل خدمة إمكانية الوصول وأن العنصر ظاهر على الشاشة."))
                        pendingClickTarget = null
                    }) { Text("تأكيد النقر") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingClickTarget = null }) { Text("إلغاء") }
                }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF0A1020), Color(0xFF111D35), Color(0xFF0A1020)))
            )
        ) {
            if (startupLoading) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF26385E),
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✦", fontSize = 54.sp, color = Color(0xFF9DB4FF), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("IslamGPT AI", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("مساعدك الذكي، أقرب إليك", color = Color(0xFFB9C7E8))
                    Spacer(Modifier.height(28.dp))
                    CircularProgressIndicator(color = Color(0xFF7C9CFF), strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("جارٍ تجهيز مساحة العمل…", color = Color(0xFFB9C7E8), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("IslamGPT AI", fontWeight = FontWeight.Bold)
                                    Text("مساعدك الشخصي", fontSize = 11.sp, color = Color(0xFFB9C7E8))
                                }
                            },
                            actions = {
                                Surface(
                                    color = if (serviceOn) Color(0xFF173E37) else Color(0xFF30384A),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Text(
                                        if (serviceOn) "● نشط" else "○ غير نشط",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        color = if (serviceOn) Color(0xFF80F0C9) else Color(0xFFD0D8EC),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF0D1628),
                                titleContentColor = Color.White
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141D31))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("الاتصال بالذكاء الاصطناعي", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("ألصق المفتاح ودع التطبيق يكتشف النماذج", color = Color(0xFFB9C7E8), style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                                        Text(if (settingsExpanded) "طيّ" else "إظهار")
                                    }
                                }
                                if (settingsExpanded) {
                                    OutlinedTextField(
                                        value = apiKey,
                                        onValueChange = { value ->
                                            apiKey = value.trim()
                                            if (value.trim().startsWith("gsk_", ignoreCase = true)) {
                                                baseUrl = GROQ_URL
                                                if (model.isBlank() || model == "gpt-4o-mini") model = DEFAULT_MODEL
                                            }
                                        },
                                        label = { Text("مفتاح API") },
                                        placeholder = { Text("ألصق مفتاح Groq هنا") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    OutlinedTextField(
                                        value = baseUrl,
                                        onValueChange = { baseUrl = it.trim() },
                                        label = { Text("عنوان API") },
                                        placeholder = { Text(GROQ_URL) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Box(Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = model,
                                            onValueChange = { model = it },
                                            label = { Text("النموذج") },
                                            placeholder = { Text("اكتشف النماذج تلقائيًا") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            trailingIcon = {
                                                TextButton(onClick = { if (models.isNotEmpty()) modelsExpanded = true else status = "اكتشف النماذج أولًا." }) {
                                                    Text("قائمة")
                                                }
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = modelsExpanded,
                                            onDismissRequest = { modelsExpanded = false },
                                            modifier = Modifier.heightIn(max = 300.dp).background(Color(0xFF202B42))
                                        ) {
                                            models.forEach { modelName ->
                                                DropdownMenuItem(
                                                    text = { Text(modelName) },
                                                    onClick = {
                                                        model = modelName
                                                        modelsExpanded = false
                                                        savePrefs(prefs, baseUrl, apiKey, model)
                                                        status = "تم اختيار النموذج وحفظ الإعدادات."
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                discovering = true
                                                status = "جارٍ التحقق من المفتاح وجلب النماذج…"
                                                try {
                                                    val found = ApiClient.discoverModels(baseUrl, apiKey)
                                                    models = found
                                                    val preferred = listOf("llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant")
                                                        .firstOrNull { it in found }
                                                    model = preferred ?: found.first()
                                                    savePrefs(prefs, baseUrl, apiKey, model)
                                                    status = "نجح الاتصال! تم العثور على ${found.size} نموذجًا واختيار $model."
                                                } catch (e: Exception) {
                                                    status = e.message ?: "تعذر اكتشاف النماذج."
                                                } finally {
                                                    discovering = false
                                                }
                                            }
                                        },
                                        enabled = !discovering && apiKey.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        if (discovering) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(9.dp))
                                        }
                                        Text(if (discovering) "جارٍ اكتشاف النماذج…" else "✦ اكتشاف تلقائي وحفظ الإعدادات")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = {
                                                savePrefs(prefs, baseUrl, apiKey, model)
                                                status = "حُفظت الإعدادات على هذا الجهاز."
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) { Text("حفظ يدوي") }
                                        OutlinedButton(
                                            onClick = {
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
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) { Text(if (serviceOn) "إيقاف المساعد" else "تشغيل المساعد") }
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10182A))
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("تحديث التطبيق", fontWeight = FontWeight.Bold)
                                            Text(
                                                updateInfo?.let { "الإصدار المتاح: ${it.versionName}" }
                                                    ?: "يفحص التطبيق عن أحدث إصدار منشور على GitHub عند التشغيل.",
                                                color = Color(0xFFB9C7E8),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    modifier = Modifier.weight(1f),
                                                    enabled = !checkingUpdate && !downloadingUpdate,
                                                    onClick = {
                                                        scope.launch {
                                                            checkingUpdate = true
                                                            try {
                                                                val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.1.0"
                                                                updateInfo = UpdateManager.checkForUpdate(current)
                                                                downloadedApk = null
                                                                status = updateInfo?.let { "يتوفر تحديث ${it.versionName}." }
                                                                    ?: "أنت تستخدم أحدث إصدار منشور."
                                                            } catch (e: Exception) {
                                                                status = e.message ?: "تعذر فحص التحديثات."
                                                            } finally {
                                                                checkingUpdate = false
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Text(if (checkingUpdate) "جارٍ الفحص…" else "فحص التحديثات")
                                                }
                                                if (updateInfo != null && downloadedApk == null) {
                                                    Button(
                                                        modifier = Modifier.weight(1f),
                                                        enabled = !downloadingUpdate && !checkingUpdate,
                                                        onClick = {
                                                            val target = updateInfo
                                                            if (target != null) scope.launch {
                                                                downloadingUpdate = true
                                                                try {
                                                                    downloadedApk = UpdateManager.downloadApk(activity, target)
                                                                    status = "اكتمل تنزيل التحديث. اضغط «تثبيت» للمتابعة."
                                                                } catch (e: Exception) {
                                                                    status = e.message ?: "تعذر تنزيل التحديث."
                                                                } finally {
                                                                    downloadingUpdate = false
                                                                }
                                                            }
                                                        }
                                                    ) {
                                                        Text(if (downloadingUpdate) "جارٍ التنزيل…" else "تنزيل")
                                                    }
                                                }
                                                if (downloadedApk != null) {
                                                    Button(
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            try {
                                                                val started = UpdateManager.installApk(activity, downloadedApk!!)
                                                                status = if (started) {
                                                                    "فتح مثبت Android. وافق على التحديث لإكماله."
                                                                } else {
                                                                    "اسمح لـ IslamGPT AI بتثبيت التطبيقات من هذا المصدر، ثم ارجع واضغط «تثبيت» مجددًا."
                                                                }
                                                            } catch (e: Exception) {
                                                                status = e.message ?: "تعذر فتح مثبت Android."
                                                            }
                                                        }
                                                    ) { Text("تثبيت") }
                                                }
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                        modifier = Modifier.align(Alignment.Start)
                                    ) { Text("إعدادات التحكم وإمكانية الوصول") }
                                }
                                if (status.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFF202B42),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(status, modifier = Modifier.padding(10.dp), color = Color(0xFFDCE5FF), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("المحادثة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { lines.clear(); lines.add(ChatLine(false, "بدأت محادثة جديدة. كيف أساعدك؟")) }) {
                                Text("محادثة جديدة")
                            }
                        }

                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(lines) { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (line.fromUser) Color(0xFF26385E) else Color(0xFF18243A),
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp, topEnd = 18.dp,
                                            bottomStart = if (line.fromUser) 18.dp else 5.dp,
                                            bottomEnd = if (line.fromUser) 5.dp else 18.dp
                                        ),
                                        modifier = Modifier.fillMaxWidth(if (line.fromUser) 0.88f else 0.96f)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(
                                                if (line.fromUser) "أنت" else "✦ IslamGPT",
                                                color = if (line.fromUser) Color(0xFF9DB4FF) else Color(0xFF80F0C9),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(line.text, color = Color(0xFFF2F5FF))
                                        }
                                    }
                                }
                            }
                            if (busy) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("يفكر ويكتب الرد…", color = Color(0xFFB9C7E8), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141D31))
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = prompt,
                                    onValueChange = { prompt = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("اكتب رسالتك هنا…") },
                                    minLines = 1,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            } else startSpeech(speechLauncher::launch)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("🎙 صوت") }
                                    Button(
                                        enabled = !busy && !discovering && prompt.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val question = prompt.trim()
                                            prompt = ""
                                            lines.add(ChatLine(true, question))
                                            when (val command = PhoneCommandExecutor.parse(question)) {
                                                PhoneCommand.Home, PhoneCommand.Back, PhoneCommand.Recents -> {
                                                    val service = AssistantAccessibilityService.instance
                                                    val succeeded = when (command) {
                                                        PhoneCommand.Home -> service?.goHome() == true
                                                        PhoneCommand.Back -> service?.goBack() == true
                                                        PhoneCommand.Recents -> service?.openRecents() == true
                                                        else -> false
                                                    }
                                                    lines.add(ChatLine(false, if (succeeded) {
                                                        when (command) {
                                                            PhoneCommand.Home -> "تم تنفيذ أمر الشاشة الرئيسية."
                                                            PhoneCommand.Back -> "تم تنفيذ أمر الرجوع."
                                                            else -> "تم فتح التطبيقات الأخيرة."
                                                        }
                                                    } else "لم أنفذ الأمر. افتح «إعدادات التحكم وإمكانية الوصول» وفعّل خدمة IslamGPT AI، ثم أعد المحاولة."))
                                                }
                                                is PhoneCommand.ClickText -> {
                                                    pendingClickTarget = command.target
                                                }
                                                is PhoneCommand.OpenPackage -> {
                                                    try {
                                                        val launch = activity.packageManager.getLaunchIntentForPackage(command.packageName)
                                                        if (launch != null) {
                                                            activity.startActivity(launch)
                                                            lines.add(ChatLine(false, "تم طلب فتح التطبيق ${command.packageName}."))
                                                        } else {
                                                            lines.add(ChatLine(false, "لم أجد تطبيقًا قابلًا للفتح بهذا المعرّف: ${command.packageName}."))
                                                        }
                                                    } catch (e: Exception) {
                                                        lines.add(ChatLine(false, "تعذر فتح التطبيق: ${e.message ?: "خطأ غير معروف"}"))
                                                    }
                                                }
                                                null -> {
                                                    busy = true
                                                    scope.launch {
                                                        try {
                                                            val answer = ApiClient.ask(baseUrl, apiKey, model, question)
                                                            lines.add(ChatLine(false, answer))
                                                        } catch (e: Exception) {
                                                            lines.add(ChatLine(false, "تعذر إكمال الطلب: ${e.message ?: "خطأ غير معروف"}"))
                                                        } finally {
                                                            busy = false
                                                            listState.animateScrollToItem(lines.lastIndex.coerceAtLeast(0))
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (busy) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(if (busy) "جارٍ الإرسال…" else "إرسال  ➤")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun savePrefs(prefs: android.content.SharedPreferences, baseUrl: String, apiKey: String, model: String) {
    prefs.edit()
        .putString("baseUrl", baseUrl.trim().ifBlank { GROQ_URL })
        .putString("apiKey", apiKey.trim())
        .putString("model", model.trim().ifBlank { DEFAULT_MODEL })
        .apply()
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
