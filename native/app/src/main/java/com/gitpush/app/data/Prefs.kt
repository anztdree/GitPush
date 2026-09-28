package com.gitpush.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("gitpush", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()

    var themeMode: String
        get() = sp.getString("themeMode", "dark") ?: "dark"
        set(v) = sp.edit().putString("themeMode", v).apply()

    var defaultCommitMsg: String
        get() = sp.getString("defaultMsg", "") ?: ""
        set(v) = sp.edit().putString("defaultMsg", v).apply()

    fun history(): List<HistoryEntry> {
        val raw = sp.getString("history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    kind = o.optString("kind"),
                    label = o.optString("label"),
                    repo = o.optString("repo"),
                    time = o.optLong("time")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistory(e: HistoryEntry) {
        val list = history().toMutableList()
        list.add(0, e)
        val capped = list.take(60)
        val arr = JSONArray()
        capped.forEach { h ->
            arr.put(JSONObject().apply {
                put("kind", h.kind)
                put("label", h.label)
                put("repo", h.repo)
                put("time", h.time)
            })
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() {
        sp.edit().putString("history", "[]").apply()
    }
}
