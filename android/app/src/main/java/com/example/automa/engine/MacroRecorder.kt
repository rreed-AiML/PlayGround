package com.example.automa.engine

import android.content.Context
import com.example.automa.model.Macro
import com.example.automa.model.MacroStep
import com.example.automa.prefs.Prefs

class MacroRecorder(private val context: Context, private val prefs: Prefs) {

    private var recording = false
    private val steps = mutableListOf<MacroStep>()
    private var lastEventTime: Long = 0

    var onStepRecorded: ((MacroStep) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    fun isRecording(): Boolean = recording

    fun startRecording(name: String = "Macro") {
        recording = true
        steps.clear()
        lastEventTime = System.currentTimeMillis()
        onStateChanged?.invoke(true)
    }

    fun stopRecording(): Macro? {
        if (!recording) return null
        recording = false
        val macro = Macro(name = "Macro", steps = steps.toList())
        val all = prefs.getMacros().toMutableList()
        all.add(macro)
        prefs.saveMacros(all)
        onStateChanged?.invoke(false)
        return macro
    }

    fun recordClick(target: String, x: Float, y: Float) {
        if (!recording) return
        val now = System.currentTimeMillis()
        val wait = if (lastEventTime == 0L) 0L else now - lastEventTime
        steps.add(MacroStep(t = "click", target = target, x = x, y = y, ms = wait.coerceAtMost(5000)))
        lastEventTime = now
        onStepRecorded?.invoke(steps.last())
    }

    fun recordType(text: String) {
        if (!recording) return
        val now = System.currentTimeMillis()
        val wait = if (lastEventTime == 0L) 0L else now - lastEventTime
        steps.add(MacroStep(t = "type", text = text, ms = wait.coerceAtMost(5000)))
        lastEventTime = now
        onStepRecorded?.invoke(steps.last())
    }

    fun recordPress(key: String) {
        if (!recording) return
        val now = System.currentTimeMillis()
        val wait = if (lastEventTime == 0L) 0L else now - lastEventTime
        steps.add(MacroStep(t = "press", key = key, ms = wait.coerceAtMost(5000)))
        lastEventTime = now
        onStepRecorded?.invoke(steps.last())
    }

    fun getSteps(): List<MacroStep> = steps.toList()
}
