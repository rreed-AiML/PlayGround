package com.example.automa.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.automa.model.ClipItem
import com.example.automa.model.Macro

class Prefs(private val context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("automa_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val TYPE_MACRO_LIST = object : TypeToken<List<Macro>>() {}.type
    private val TYPE_CLIP_LIST = object : TypeToken<List<ClipItem>>() {}.type

    fun getMacros(): List<Macro> {
        val json = sp.getString("macros", null)
        return if (json.isNullOrEmpty()) emptyList() else gson.fromJson(json, TYPE_MACRO_LIST)
    }
    fun saveMacros(list: List<Macro>) {
        sp.edit().putString("macros", gson.toJson(list, TYPE_MACRO_LIST)).apply()
    }

    fun getClipboard(): List<ClipItem> {
        val json = sp.getString("clipboard", null)
        return if (json.isNullOrEmpty()) emptyList() else gson.fromJson(json, TYPE_CLIP_LIST)
    }
    fun saveClipboard(list: List<ClipItem>) {
        sp.edit().putString("clipboard", gson.toJson(list, TYPE_CLIP_LIST)).apply()
    }

    fun getScript(): String = sp.getString("script", "") ?: ""
    fun saveScript(text: String) { sp.edit().putString("script", text).apply() }

    fun getNotes(): String = sp.getString("notes", "") ?: ""
    fun saveNotes(text: String) { sp.edit().putString("notes", text).apply() }

    fun getBool(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    fun putBool(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    fun getString(key: String, default: String): String = sp.getString(key, default) ?: default
    fun putString(key: String, value: String) { sp.edit().putString(key, value).apply() }
}
