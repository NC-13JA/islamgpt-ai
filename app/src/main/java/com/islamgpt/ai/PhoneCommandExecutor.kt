package com.islamgpt.ai

/**
 * Intentionally small, local command set. Unknown commands are sent to the AI as ordinary chat.
 * Sensitive UI clicks are returned as confirmation requests by the caller.
 */
sealed class PhoneCommand {
    data object Home : PhoneCommand()
    data object Back : PhoneCommand()
    data object Recents : PhoneCommand()
    data class ClickText(val target: String) : PhoneCommand()
    data class OpenPackage(val packageName: String) : PhoneCommand()
}

object PhoneCommandExecutor {
    private fun normalize(value: String): String = value.lowercase()
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace("ى", "ي").replace("ة", "ه")

    fun parse(input: String): PhoneCommand? {
        val text = input.trim()
        val normalized = normalize(text)

        if (normalized in setOf("الرئيسيه", "الشاشه الرئيسيه", "اذهب للرئيسيه", "اذهب الي الشاشه الرئيسيه", "افتح الشاشه الرئيسيه", "home")) {
            return PhoneCommand.Home
        }
        if (normalized in setOf("ارجع", "عوده", "رجوع", "اضغط رجوع", "back")) return PhoneCommand.Back
        if (normalized in setOf("التطبيقات الاخيره", "التطبيقات المفتوحه", "المهام الاخيره", "recent apps", "recents")) {
            return PhoneCommand.Recents
        }

        val clickPrefixes = listOf("انقر على ", "اضغط على ", "انقر علي ", "اضغط علي ", "click ").map(::normalize)
        clickPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val target = text.drop(prefix.length).trim()
            if (target.isNotBlank()) return PhoneCommand.ClickText(target)
        }

        val appPrefixes = listOf("افتح تطبيق ", "شغل تطبيق ", "open app ").map(::normalize)
        appPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val packageName = text.drop(prefix.length).trim()
            if (packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) {
                return PhoneCommand.OpenPackage(packageName)
            }
        }

        val aliases = mapOf(
            "واتساب" to "com.whatsapp", "whatsapp" to "com.whatsapp",
            "يوتيوب" to "com.google.android.youtube", "youtube" to "com.google.android.youtube",
            "كروم" to "com.android.chrome", "chrome" to "com.android.chrome",
            "الاعدادات" to "com.android.settings", "settings" to "com.android.settings",
            "تيليجرام" to "org.telegram.messenger", "telegram" to "org.telegram.messenger",
            "انستغرام" to "com.instagram.android", "instagram" to "com.instagram.android",
            "تيك توك" to "com.zhiliaoapp.musically", "tiktok" to "com.zhiliaoapp.musically",
            "فيسبوك" to "com.facebook.katana", "facebook" to "com.facebook.katana"
        )
        val simpleOpenPrefixes = listOf("افتح ", "شغل ", "open ").map(::normalize)
        simpleOpenPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val requested = normalized.removePrefix(prefix).trim()
            aliases[requested]?.let { return PhoneCommand.OpenPackage(it) }
            if (requested.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) {
                return PhoneCommand.OpenPackage(requested)
            }
        }
        return null
    }
}
