package com.tabgl.kaleido.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortOrder { NEWEST, OLDEST, NAME }

/**
 * User settings, starred photos and WebDAV state, saved in SharedPreferences. Values the UI shows
 * are observable state, so changes appear immediately.
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
    var starred by mutableStateOf(readIds(KEY_STARRED))
        private set

    var webdavUrl by mutableStateOf(prefs.getString(KEY_WEBDAV_URL, "").orEmpty())
        private set
    var webdavUser by mutableStateOf(prefs.getString(KEY_WEBDAV_USER, "").orEmpty())
        private set
    var webdavPassword by mutableStateOf(readPassword())
        private set
    var lastSync by mutableStateOf(prefs.getString(KEY_LAST_SYNC, "").orEmpty())
    var displayName by mutableStateOf(prefs.getString(KEY_NAME, "").orEmpty())
        private set

    /** A short random tag that keeps this phone's uploads apart from other devices' on the same server. */
    val deviceTag: String = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().take(8).also {
        prefs.edit().putString(KEY_DEVICE, it).apply()
    }

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

    fun saveWebDav(url: String, user: String, password: String) {
        webdavUrl = url
        webdavUser = user
        webdavPassword = password
        prefs.edit()
            .putString(KEY_WEBDAV_URL, url)
            .putString(KEY_WEBDAV_USER, user)
            .putString(KEY_WEBDAV_PASS, runCatching { SecureStore.encrypt(password) }.getOrDefault(""))
            .apply()
    }

    fun saveDisplayName(name: String) {
        displayName = name.take(40)
        prefs.edit().putString(KEY_NAME, displayName).apply()
    }

    fun saveLastSync(text: String) {
        lastSync = text
        prefs.edit().putString(KEY_LAST_SYNC, text).apply()
    }

    /** Photos this phone has already uploaded to the WebDAV server. */
    fun syncedIds(): Set<Long> = readIds(KEY_SYNCED)

    fun saveSyncedIds(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_SYNCED, ids.mapTo(HashSet()) { it.toString() }).apply()
    }

    /** Remote file names already downloaded to this phone. */
    fun downloadedNames(): Set<String> = prefs.getStringSet(KEY_DOWNLOADED, emptySet()).orEmpty().toSet()

    fun saveDownloadedNames(names: Set<String>) {
        prefs.edit().putStringSet(KEY_DOWNLOADED, HashSet(names)).apply()
    }

    private fun readIds(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    private fun readPassword(): String {
        val stored = prefs.getString(KEY_WEBDAV_PASS, "").orEmpty()
        if (stored.isEmpty()) return ""
        return runCatching { SecureStore.decrypt(stored) }.getOrDefault("")
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_COLUMNS = "columns"
        const val KEY_SORT = "sort"
        const val KEY_STARRED = "starred"
        const val KEY_WEBDAV_URL = "webdav_url"
        const val KEY_WEBDAV_USER = "webdav_user"
        const val KEY_WEBDAV_PASS = "webdav_pass"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_NAME = "display_name"
        const val KEY_DEVICE = "device_tag"
        const val KEY_SYNCED = "synced_ids"
        const val KEY_DOWNLOADED = "downloaded_names"
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
