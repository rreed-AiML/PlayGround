package com.example.automa.engine

import android.os.Handler
import android.os.Looper
import com.example.automa.accessibility.AutomationAccessibilityService
import com.example.automa.model.MacroStep

class MacroPlayer(private val accessibilityService: AutomationAccessibilityService?) {

    private var playing = false
    private var stopRequested = false

    var onStepStarted: ((Int, MacroStep) -> Unit)? = null
    var onFinished: ((Boolean) -> Unit)? = null

    fun isPlaying(): Boolean = playing

    fun play(steps: List<MacroStep>) {
        if (playing) return
        playing = true
        stopRequested = false
        executeSteps(steps, 0)
    }

    fun stop() {
        stopRequested = true
    }

    private fun executeSteps(steps: List<MacroStep>, index: Int) {
        if (stopRequested || index >= steps.size) {
            finishPlaying(completed = !stopRequested)
            return
        }
        val step = steps[index]
        onStepStarted?.invoke(index, step)

        when (step.t) {
            "click" -> accessibilityService?.performGlobalClickByTextDirect(step.target ?: "")
            "type" -> accessibilityService?.performGlobalType(step.text ?: "")
            "press" -> accessibilityService?.performGlobalNavigation(step.key ?: "back")
        }

        val nextIndex = index + 1
        if (nextIndex < steps.size && !stopRequested) {
            Handler(Looper.getMainLooper()).postDelayed({
                executeSteps(steps, nextIndex)
            }, step.ms.coerceAtLeast(250))
        } else {
            finishPlaying(completed = true)
        }
    }

    private fun finishPlaying(completed: Boolean) {
        playing = false
        onFinished?.invoke(completed)
    }
}
