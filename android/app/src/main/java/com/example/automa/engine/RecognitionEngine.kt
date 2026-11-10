package com.example.automa.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.automa.accessibility.AutomationAccessibilityService

class RecognitionEngine(
    private val accessibility: AutomationAccessibilityService?
) {

    fun findText(query: String): List<kotlin.Pair<Int, String>> =
        accessibility?.findTextNodes(query) ?: emptyList()

    fun detectByRole(role: String): List<kotlin.Pair<Int, String>> =
        accessibility?.detectByRole(role) ?: emptyList()

    fun matchTemplate(template: Bitmap): List<kotlin.Pair<Int, String>> =
        accessibility?.matchTemplateColors(Color.BLACK) ?: emptyList()
}
