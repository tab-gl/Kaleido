package com.tabgl.kaleido.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortOrder { NEWEST, OLDEST, NAME }

/**
 * User settings and starred photo ids, saved in SharedPreferences. Each value is observable state,
 * so the UI updates as soon as a setting changes.
 */
class AppSettings(private val prefs: SharedPreferences) {
    var themeMode by mutableStateOf(enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM))
        private set
    var dynamicColor by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC, true))
        private set
    var columns by mutableStateOf(prefs.getInt(KEY_COLUMNS, 3).coerceIn(3, 5))
        private set
    var sortOrder by mutableStateOf(enumOr(prefs.getString(KEY_SORT, null), SortOrder.NEWEST))
        private set
    var starred by mutableStateOf(readStarred())
        private set

    fun saveThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun saveDynamicColor(on: Boolean) {
        dynamicColor = on
        prefs.edit().putBoolean(KEY_DYNAMIC, on).apply()
    }

    fun saveColumns(count: Int) {
        columns = count.coerceIn(3, 5)
        prefs.edit().putInt(KEY_COLUMNS, columns).apply()
    }

    fun saveSortOrder(order: SortOrder) {
        sortOrder = order
        prefs.edit().putString(KEY_SORT, order.name).apply()
    }

    fun saveStarred(ids: Set<Long>) {
        starred = ids
        prefs.edit().putStringSet(KEY_STARRED, ids.mapTo(HashSet()) { it.toString() }).apply()
    }

    private fun readStarred(): Set<Long> =
        prefs.getStringSet(KEY_STARRED, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_COLUMNS = "columns"
        const val KEY_SORT = "sort"
        const val KEY_STARRED = "starred"
    }
}

/** Parses a saved enum name, falling back to [fallback] for missing or unknown values. */
private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember { AppSettings(context.getSharedPreferences("kaleido", Context.MODE_PRIVATE)) }
}
