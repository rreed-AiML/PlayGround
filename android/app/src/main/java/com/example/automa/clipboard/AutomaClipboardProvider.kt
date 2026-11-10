package com.example.automa.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.content.ClipboardManager
import android.content.ClipData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.automa.model.ClipItem

class AutomaClipboardProvider : ContentProvider() {

    private lateinit var clipMgr: ClipboardManager
    private lateinit var sp: android.content.SharedPreferences
    private val gson = Gson()
    private val TYPE_CLIP = object : TypeToken<List<ClipItem>>() {}.type

    override fun onCreate(): Boolean {
        sp = context!!.getSharedPreferences("automa_prefs", Context.MODE_PRIVATE)
        clipMgr = context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.lastPathSegment != "history") return null
        val cursor = MatrixCursor(arrayOf("_id", "text", "ts"))
        val list = readHistory()
        for ((i, item) in list.withIndex()) {
            cursor.addRow(arrayOf<Any?>(i.toLong(), item.text, item.ts))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? =
        "vnd.android.cursor.dir/com.example.automa.clipitem"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val text = values?.get("text") as? String ?: return null
        val item = ClipItem(text)
        val list = readHistory().toMutableList()
        list.removeAll { it.text == text }
        list.add(0, item)
        if (list.size > 50) list.removeAt(list.size - 1)
        writeHistory(list)
        clipMgr.setPrimaryClip(ClipData.newPlainText("automa", text))
        return Uri.parse("content://com.example.automa.clipboard/history/${System.currentTimeMillis()}")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    private fun readHistory(): List<ClipItem> {
        val json = sp.getString("clipboard", null)
        return if (json.isNullOrEmpty()) emptyList() else gson.fromJson(json, TYPE_CLIP)
    }
    private fun writeHistory(list: List<ClipItem>) {
        sp.edit().putString("clipboard", gson.toJson(list, TYPE_CLIP)).apply()
    }
}
