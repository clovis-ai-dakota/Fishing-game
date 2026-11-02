package com.deepline.fishing

import android.content.Context
import com.deepline.fishing.core.KeyValueStore

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("deepline", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
