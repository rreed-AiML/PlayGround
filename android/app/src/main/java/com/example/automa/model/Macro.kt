package com.example.automa.model

import java.util.*

data class MacroStep(
    val t: String,
    val target: String? = null,
    val text: String? = null,
    val ms: Long = 300,
    val x: Float = 0f,
    val y: Float = 0f,
    val key: String? = null
)

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep> = emptyList(),
    var created: Long = System.currentTimeMillis(),
    var lastRun: Long? = null
)

data class ClipItem(
    val text: String,
    val ts: Long = System.currentTimeMillis()
)
