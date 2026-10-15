package com.islamgpt.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Opt-in UI automation bridge. No event content is logged or transmitted. */
class AssistantAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null
            private set
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun clickVisibleText(target: String): Boolean {
        if (target.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        return try {
            val node = findTextNode(root, target) ?: return false
            try {
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) {
                    val parent = clickable.parent
                    if (clickable !== node) clickable.recycle()
                    clickable = parent
                }
                clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            } finally {
                node.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun findTextNode(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.contains(target, ignoreCase = true) || desc.contains(target, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTextNode(child, target)
            child.recycle()
            if (found != null) return found
        }
        return null
    }
}
