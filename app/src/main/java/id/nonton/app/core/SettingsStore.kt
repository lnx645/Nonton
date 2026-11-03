package id.nonton.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nonton_settings", Context.MODE_PRIVATE)

    private val _selectedServiceId = MutableStateFlow(prefs.getInt(KEY_SERVICE, 0))
    val selectedServiceId: StateFlow<Int> = _selectedServiceId.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _pipEnabled = MutableStateFlow(prefs.getBoolean(KEY_PIP, true))
    val pipEnabled: StateFlow<Boolean> = _pipEnabled.asStateFlow()

    // Riwayat pencarian dipakai untuk personalisasi feed Video. Disimpan
    // sebagai JSON sederhana agar tidak perlu migrasi database.
    private val _searchHistory = MutableStateFlow(readSearchHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun recordSearch(query: String) {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length < 2) return
        val current = readSearchHistory()
        val updated = (current + normalized).takeLast(MAX_SEARCH_HISTORY)
        prefs.edit().putString(KEY_SEARCH_HISTORY, encodeSearchHistory(updated)).apply()
        _searchHistory.value = updated
    }

    private fun readSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSearchHistory(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    var defaultQuality: String
        get() = prefs.getString(KEY_QUALITY, "best") ?: "best"
        set(value) = prefs.edit().putString(KEY_QUALITY, value).apply()

    fun setSelectedService(id: Int) {
        prefs.edit().putInt(KEY_SERVICE, id).apply()
        _selectedServiceId.value = id
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setPipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIP, enabled).apply()
        _pipEnabled.value = enabled
    }

    private companion object {
        const val KEY_SERVICE = "selected_service"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_PIP = "pip_enabled"
        const val KEY_QUALITY = "default_quality"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val MAX_SEARCH_HISTORY = 60
    }
}
