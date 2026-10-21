package com.cleanurl.app.data

import android.content.Context
import android.content.SharedPreferences
import com.cleanurl.app.core.CleanOptions
import com.cleanurl.app.core.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val expandShortLinks: Boolean = true,
    val convertShorts: Boolean = false,
    val toastOnAndroid13: Boolean = false,
) {
    fun toCleanOptions() = CleanOptions(
        convertShorts = convertShorts,
        expandShortLinks = expandShortLinks,
    )
}

data class HistoryEntry(
    val platform: Platform,
    val url: String,
    val timestamp: Long,
)

/** Settings and the most recent [MAX_HISTORY] cleaned links, backed by SharedPreferences. */
class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("cleanurl_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun updateSettings(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_EXPAND_FB, updated.expandShortLinks)
            .putBoolean(KEY_CONVERT_SHORTS, updated.convertShorts)
            .putBoolean(KEY_TOAST_13, updated.toastOnAndroid13)
            .apply()
        _settings.value = updated
    }

    /** Adds [entry] at the top, dropping any older entry with the same URL. */
    fun addHistory(entry: HistoryEntry) {
        val updated = (listOf(entry) + _history.value.filterNot { it.url == entry.url })
            .take(MAX_HISTORY)
        saveHistory(updated)
    }

    fun clearHistory() = saveHistory(emptyList())

    /** Re-read from disk, e.g. after the share receiver added an entry. */
    fun reload() {
        _settings.value = loadSettings()
        _history.value = loadHistory()
    }

    private fun saveHistory(entries: List<HistoryEntry>) {
        val json = JSONArray()
        entries.forEach {
            json.put(
                JSONObject()
                    .put("platform", it.platform.name)
                    .put("url", it.url)
                    .put("time", it.timestamp),
            )
        }
        prefs.edit().putString(KEY_HISTORY, json.toString()).apply()
        _history.value = entries
    }

    private fun loadSettings() = Settings(
        expandShortLinks = prefs.getBoolean(KEY_EXPAND_FB, true),
        convertShorts = prefs.getBoolean(KEY_CONVERT_SHORTS, false),
        toastOnAndroid13 = prefs.getBoolean(KEY_TOAST_13, false),
    )

    private fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                val obj = json.getJSONObject(i)
                val platform = Platform.entries.firstOrNull { it.name == obj.optString("platform") }
                    ?: Platform.GENERIC
                HistoryEntry(platform, obj.getString("url"), obj.getLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_HISTORY = 20

        private const val KEY_EXPAND_FB = "expand_facebook_short_links"
        private const val KEY_CONVERT_SHORTS = "convert_shorts"
        private const val KEY_TOAST_13 = "toast_on_android_13"
        private const val KEY_HISTORY = "history"

        @Volatile
        private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
    }
}
