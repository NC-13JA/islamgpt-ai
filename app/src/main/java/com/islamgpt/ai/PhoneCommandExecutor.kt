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
    fun parse(input: String): PhoneCommand? {
        val text = input.trim()
        val normalized = text.lowercase()
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ى", "ي").replace("ة", "ه")

        if (normalized in setOf("الرئيسيه", "الشاشه الرئيسيه", "اذهب للرئيسيه", "اذهب الي الشاشه الرئيسيه", "افتح الشاشه الرئيسيه", "home")) {
            return PhoneCommand.Home
        }
        if (normalized in setOf("ارجع", "عوده", "رجوع", "اضغط رجوع", "back")) return PhoneCommand.Back
        if (normalized in setOf("التطبيقات الاخيره", "التطبيقات المفتوحه", "المهام الاخيره", "recent apps", "recents")) {
            return PhoneCommand.Recents
        }

        val clickPrefixes = listOf("انقر على ", "اضغط على ", "انقر علي ", "اضغط علي ", "click ")
        clickPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val target = text.drop(prefix.length).trim()
            if (target.isNotBlank()) return PhoneCommand.ClickText(target)
        }

        val appPrefixes = listOf("افتح تطبيق ", "شغل تطبيق ", "open app ")
        appPrefixes.firstOrNull { normalized.startsWith(it) }?.let { prefix ->
            val packageName = text.drop(prefix.length).trim()
            if (packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) {
                return PhoneCommand.OpenPackage(packageName)
            }
        }
        return null
    }
}
