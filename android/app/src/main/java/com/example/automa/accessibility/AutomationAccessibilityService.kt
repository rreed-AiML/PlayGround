package com.example.automa.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.automa.model.MacroStep

class AutomationAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var onEvent: ((AccessibilityEvent) -> Unit)? = null
    private var onRecorderStep: ((MacroStep) -> Unit)? = null
    private var recordingEnabled = false

    override fun onServiceConnected() {
        AutomationAccessibilityService.instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AutomationAccessibilityService.instance = this
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.post {
            onEvent?.invoke(event)
            if (recordingEnabled && onRecorderStep != null) {
                eventToStep(event)?.let { onRecorderStep?.invoke(it) }
            }
        }
    }

    override fun onInterrupt() {}

    fun setEventListener(listener: (AccessibilityEvent) -> Unit) { onEvent = listener }
    fun setRecorderStepListener(listener: (MacroStep) -> Unit) { onRecorderStep = listener }
    fun setRecording(enabled: Boolean) { recordingEnabled = enabled }

    companion object {
        var instance: AutomationAccessibilityService? = null
    }

    private fun roots(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val root = rootInActiveWindow
        if (root != null) out.add(root)
        return out
    }

    fun findTextNodes(query: String): List<kotlin.Pair<Int, String>> {
        val q = query.lowercase()
        val out = mutableListOf<kotlin.Pair<Int, String>>()
        for (root in roots()) {
            collectTextMatches(root, q, out)
            root.recycle()
        }
        return out
    }

    fun detectByRole(role: String): List<kotlin.Pair<Int, String>> {
        val out = mutableListOf<kotlin.Pair<Int, String>>()
        for (root in roots()) {
            collectByRole(root, role, out)
            root.recycle()
        }
        return out
    }

    fun matchTemplateColors(placeholder: Int): List<kotlin.Pair<Int, String>> {
        val out = mutableListOf<kotlin.Pair<Int, String>>()
        for (root in roots()) {
            collectImageNodes(root, out)
            root.recycle()
        }
        return out
    }

    fun findTextOnScreen(needle: String): Boolean = findTextNodes(needle).isNotEmpty()

    fun performGlobalClickByText(query: String): Boolean = performGlobalClickByTextDirect(query)

    fun performGlobalClickByTextDirect(query: String): Boolean {
        val node = findFirstNode(query)
        if (node == null) return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            true
        } catch (e: Exception) {
            false
        } finally {
            node.recycle()
        }
    }

    fun performGlobalType(text: String): Boolean {
        val origin = rootInActiveWindow ?: return false
        val focused = origin.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: origin.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val target = focused ?: origin
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                val b = android.os.Bundle()
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
                true
            } else false
        } finally {
            if (focused != origin) focused.recycle()
            origin.recycle()
        }
    }

    fun performGlobalNavigation(key: String): Boolean {
        val action = when (key) {
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> AccessibilityService.GLOBAL_ACTION_BACK
        }
        return try {
            performGlobalAction(action)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun collectTextMatches(node: AccessibilityNodeInfo, q: String,
                                   out: MutableList<kotlin.Pair<Int, String>>) {
        val text = (node.text ?: node.contentDescription ?: "").toString().lowercase()
        if (text.contains(q)) {
            out.add(Pair(node.hashCode(), text))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextMatches(child, q, out)
            child.recycle()
        }
    }

    private fun findFirstNode(query: String): AccessibilityNodeInfo? {
        val q = query.lowercase()
        for (root in roots()) {
            val found = findTextMatch(root, q)
            root.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findTextMatch(node: AccessibilityNodeInfo, q: String): AccessibilityNodeInfo? {
        val text = (node.text ?: node.contentDescription ?: "").toString().lowercase()
        if (text.contains(q)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTextMatch(child, q)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun collectByRole(node: AccessibilityNodeInfo, role: String,
                              out: MutableList<kotlin.Pair<Int, String>>) {
        val className = node.className ?: ""
        val label = (node.text ?: node.contentDescription ?: className).toString()
        val r = role.lowercase()
        val match = when (r) {
            "button" -> className.contains("Button", ignoreCase = true) ||
                node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            "text field", "textfield" -> className.contains("EditText", ignoreCase = true) ||
                className.contains("TextView", ignoreCase = true)
            "image", "icon" -> className.contains("Image", ignoreCase = true)
            else -> true
        }
        if (match) out.add(Pair(node.hashCode(), label))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByRole(child, role, out)
            child.recycle()
        }
    }

    private fun collectImageNodes(node: AccessibilityNodeInfo,
                                  out: MutableList<kotlin.Pair<Int, String>>) {
        val className = node.className ?: ""
        if (className.contains("Image", ignoreCase = true)) {
            out.add(Pair(node.hashCode(),
                (node.contentDescription ?: node.text ?: "Image").toString()))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectImageNodes(child, out)
            child.recycle()
        }
    }

    private fun eventToStep(event: AccessibilityEvent): MacroStep? {
        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val txt = event.text?.firstOrNull()?.toString() ?: ""
                MacroStep(t = "click", target = txt)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val txt = event.text?.firstOrNull()?.toString() ?: ""
                MacroStep(t = "type", text = txt)
            }
            else -> null
        }
    }
}
