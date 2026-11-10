package com.example.automa.input

import android.view.View
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import com.example.automa.R

class CustomKeyboardService : InputMethodService() {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.keyboard)
        keyboardView = KeyboardView(this, null).apply {
            setKeyboard(keyboard)
            isPreviewEnabled = false
        }
        return keyboardView
    }

    override fun onCreateCandidatesView(): View? = null
}
