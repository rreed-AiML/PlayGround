package com.example.automa.engine

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.automa.accessibility.AutomationAccessibilityService
import com.example.automa.model.MacroStep

class ScriptEngine(
    private val context: android.content.Context,
    private val accessibility: AutomationAccessibilityService?,
    private val prefs: androidx.preference.PreferenceManager? = null
) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var stopRequested = false

    var onLog: ((String, Boolean) -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    fun isRunning(): Boolean = running

    fun run(source: String) {
        if (running) return
        running = true
        stopRequested = false
        try {
            val lines = preprocess(source)
            val stmts = parseBlock(lines, 0).first
            executeStatements(stmts, 0) { completed ->
                running = false
                if (!stopRequested && completed) onFinished?.invoke()
            }
        } catch (e: Exception) {
            running = false
            onLog?.invoke("Script error: ${e.message}", true)
            onFinished?.invoke()
        }
    }

    fun stop() {
        stopRequested = true
    }

    private fun preprocess(source: String): List<String> {
        return source.lines().map { line ->
            val idx = line.indexOf("//")
            val cleaned = if (idx >= 0) line.substring(0, idx) else line
            cleaned.trim()
        }.filter { it.isNotEmpty() }
    }

    private fun parseBlock(lines: List<String>, index: Int): Pair<List<Stmt>, Int> {
        val stmts = mutableListOf<Stmt>()
        var i = index
        while (i < lines.size) {
            val line = lines[i]
            when {
                line == "}" -> return Pair(stmts, i + 1)
                line.startsWith("repeat ") -> {
                    val m = """^repeat\s+(\d+)\s*\{?\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid repeat: $line")
                    val n = m.groupValues[1].toInt()
                    val (block, next) = parseBlock(lines, i + 1)
                    stmts.add(Stmt.Repeat(n, block))
                    i = next
                }
                line.startsWith("if found ") -> {
                    val m = """^if found\s+"([^"]*)"\s*\{?\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid if: $line")
                    val needle = m.groupValues[1]
                    val (thenBlock, next) = parseBlock(lines, i + 1)
                    var elseBlock: List<Stmt>? = null
                    var ni = next
                    if (ni < lines.size && lines[ni] == "else") {
                        val (eb, ebNext) = parseBlock(lines, ni + 1)
                        elseBlock = eb
                        ni = ebNext
                    }
                    stmts.add(Stmt.IfFound(needle, thenBlock, elseBlock))
                    i = ni
                }
                line == "else" -> throw IllegalArgumentException("Else without if at line $i")
                line.startsWith("click ") -> {
                    val m = """^click\s+"([^"]*)"\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid click: $line")
                    stmts.add(Stmt.Cmd(MacroStep(t = "click", target = m.groupValues[1])))
                    i++
                }
                line.startsWith("type ") -> {
                    val m = """^type\s+"([^"]*)"\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid type: $line")
                    stmts.add(Stmt.Cmd(MacroStep(t = "type", text = m.groupValues[1])))
                    i++
                }
                line.startsWith("wait ") -> {
                    val m = """^wait\s+(\d+)\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid wait: $line")
                    stmts.add(Stmt.Cmd(MacroStep(t = "wait", ms = m.groupValues[1].toLong())))
                    i++
                }
                line.startsWith("notify ") -> {
                    val m = """^notify\s+"([^"]*)"\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid notify: $line")
                    stmts.add(Stmt.Notify(m.groupValues[1]))
                    i++
                }
                line == "screenshot" -> {
                    stmts.add(Stmt.Screenshot)
                    i++
                }
                line.startsWith("press ") -> {
                    val m = """^press\s+(back|home|recent)\s*$""".toRegex().matchEntire(line)
                        ?: throw IllegalArgumentException("Invalid press: $line")
                    stmts.add(Stmt.Cmd(MacroStep(t = "press", key = m.groupValues[1])))
                    i++
                }
                else -> throw IllegalArgumentException("Unknown command: $line")
            }
        }
        return Pair(stmts, i)
    }

    private sealed class Stmt {
        data class Cmd(val step: MacroStep) : Stmt()
        data class Repeat(val n: Int, val block: List<Stmt>) : Stmt()
        data class IfFound(val needle: String, val thenBlock: List<Stmt>, val elseBlock: List<Stmt>?) : Stmt()
        data class Notify(val msg: String) : Stmt()
        object Screenshot : Stmt()
    }

    private fun executeStatements(stmts: List<Stmt>, index: Int, onDone: (Boolean) -> Unit) {
        if (stopRequested || index >= stmts.size) {
            onDone(true)
            return
        }
        val stmt = stmts[index]
        when (stmt) {
            is Stmt.Cmd -> {
                executeCmd(stmt.step)
                handler.postDelayed({ executeStatements(stmts, index + 1, onDone) }, 250L)
            }
            is Stmt.Repeat -> {
                executeRepeat(stmt, 0, { executeStatements(stmts, index + 1, onDone) })
            }
            is Stmt.IfFound -> {
                val found = accessibility?.findTextOnScreen(stmt.needle) == true
                val block = if (found) stmt.thenBlock else (stmt.elseBlock ?: emptyList())
                executeStatements(block, 0, { executeStatements(stmts, index + 1, onDone) })
            }
            is Stmt.Notify -> {
                Toast.makeText(context, stmt.msg, Toast.LENGTH_SHORT).show()
                onLog?.invoke("notify: ${stmt.msg}", false)
                handler.postDelayed({ executeStatements(stmts, index + 1, onDone) }, 200L)
            }
            is Stmt.Screenshot -> {
                onLog?.invoke("screenshot captured", false)
                handler.postDelayed({ executeStatements(stmts, index + 1, onDone) }, 200L)
            }
        }
    }

    private fun executeRepeat(stmt: Stmt.Repeat, iteration: Int, onDone: () -> Unit) {
        if (stopRequested || iteration >= stmt.n) {
            onDone()
            return
        }
        executeStatements(stmt.block, 0, {
            executeRepeat(stmt, iteration + 1, onDone)
        })
    }

    private fun executeCmd(step: MacroStep) {
        when (step.t) {
            "click" -> accessibility?.performGlobalClickByTextDirect(step.target ?: "")
            "type" -> accessibility?.performGlobalType(step.text ?: "")
            "press" -> accessibility?.performGlobalNavigation(step.key ?: "back")
        }
        onLog?.invoke("${step.t} ${step.target ?: step.text ?: step.key ?: ""}", false)
    }
}
