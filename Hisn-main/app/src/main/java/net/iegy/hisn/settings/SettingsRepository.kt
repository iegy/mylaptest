package net.iegy.hisn.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.browser.SearchEngine

/** One DataStore per process; only the main process ever touches it (the private process receives a snapshot). */
val Context.hisnDataStore: DataStore<Preferences> by preferencesDataStore(name = "hisn_settings")

class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val mirror: BootMirror? = null,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val BLOCKING = booleanPreferencesKey("blocking_enabled")
        val AUTO_UPDATE_FILTERS = booleanPreferencesKey("auto_update_filters")
        val ALLOWLIST = stringSetPreferencesKey("allowlist")
        val NEW_TAB_PAGE = stringPreferencesKey("new_tab_page")
        val CUSTOM_HOME_URL = stringPreferencesKey("custom_home_url")
        val STARTUP = stringPreferencesKey("startup")
        val DESKTOP_DEFAULT = booleanPreferencesKey("desktop_default")
        val SHOW_RECENT = booleanPreferencesKey("show_recent")
        val LAST_FILTER_UPDATE = longPreferencesKey("last_filter_update")
        val SHORTCUTS_SEEDED = booleanPreferencesKey("shortcuts_seeded")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val JS_BLOCKED = stringSetPreferencesKey("js_blocked_hosts")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
        val BACKGROUND_MEDIA = booleanPreferencesKey("background_media")
        val PRAYER_NOTIFICATIONS = booleanPreferencesKey("prayer_notifications")
    }

    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun setTheme(value: ThemeMode) {
        mirror?.onThemeChanged(value)
        store.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setLanguage(value: AppLanguage) {
        // Mirrored first so the recreated activity already sees the new language.
        mirror?.onLanguageChanged(value)
        store.edit { it[Keys.LANGUAGE] = value.name }
    }

    suspend fun setSearchEngine(value: SearchEngine) {
        store.edit { it[Keys.SEARCH_ENGINE] = value.id }
    }

    suspend fun setBlockingEnabled(value: Boolean) {
        store.edit { it[Keys.BLOCKING] = value }
    }

    suspend fun setAutoUpdateFilters(value: Boolean) {
        store.edit { it[Keys.AUTO_UPDATE_FILTERS] = value }
    }

    suspend fun setNewTabPage(value: NewTabPage) {
        store.edit { it[Keys.NEW_TAB_PAGE] = value.name }
    }

    suspend fun setCustomHomeUrl(value: String) {
        store.edit { it[Keys.CUSTOM_HOME_URL] = value }
    }

    suspend fun setStartup(value: StartupBehavior) {
        store.edit { it[Keys.STARTUP] = value.name }
    }

    suspend fun setDesktopByDefault(value: Boolean) {
        store.edit { it[Keys.DESKTOP_DEFAULT] = value }
    }

    suspend fun setShowRecent(value: Boolean) {
        store.edit { it[Keys.SHOW_RECENT] = value }
    }

    suspend fun setLastFilterUpdate(value: Long) {
        store.edit { it[Keys.LAST_FILTER_UPDATE] = value }
    }

    suspend fun setShortcutsSeeded() {
        store.edit { it[Keys.SHORTCUTS_SEEDED] = true }
    }

    suspend fun setTextZoom(value: Int) {
        store.edit { it[Keys.TEXT_ZOOM] = value.coerceIn(50, 250) }
    }

    suspend fun setHttpsOnly(value: Boolean) {
        store.edit { it[Keys.HTTPS_ONLY] = value }
    }

    suspend fun setAppLock(value: Boolean) {
        mirror?.onAppLockChanged(value)
        store.edit { it[Keys.APP_LOCK] = value }
    }

    suspend fun setClearOnExit(value: Boolean) {
        store.edit { it[Keys.CLEAR_ON_EXIT] = value }
    }

    suspend fun setBackgroundMedia(value: Boolean) {
        store.edit { it[Keys.BACKGROUND_MEDIA] = value }
    }

    suspend fun setPrayerNotifications(value: Boolean) {
        store.edit { it[Keys.PRAYER_NOTIFICATIONS] = value }
    }

    suspend fun addJsBlocked(host: String) {
        val normalized = Allowlist.normalize(host)
        if (normalized.isEmpty()) return
        store.edit { it[Keys.JS_BLOCKED] = (it[Keys.JS_BLOCKED] ?: emptySet()) + normalized }
    }

    suspend fun removeJsBlocked(host: String) {
        val normalized = Allowlist.normalize(host)
        store.edit { it[Keys.JS_BLOCKED] = (it[Keys.JS_BLOCKED] ?: emptySet()) - normalized }
    }

    suspend fun addToAllowlist(host: String) {
        val normalized = Allowlist.normalize(host)
        if (normalized.isEmpty()) return
        store.edit { it[Keys.ALLOWLIST] = (it[Keys.ALLOWLIST] ?: emptySet()) + normalized }
    }

    suspend fun removeFromAllowlist(host: String) {
        val normalized = Allowlist.normalize(host)
        store.edit { it[Keys.ALLOWLIST] = (it[Keys.ALLOWLIST] ?: emptySet()) - normalized }
    }

    private fun Preferences.toSettings() = Settings(
        theme = enumOrDefault(this[Keys.THEME], ThemeMode.SYSTEM),
        language = enumOrDefault(this[Keys.LANGUAGE], AppLanguage.SYSTEM),
        searchEngine = SearchEngine.fromId(this[Keys.SEARCH_ENGINE]),
        blockingEnabled = this[Keys.BLOCKING] ?: true,
        autoUpdateFilters = this[Keys.AUTO_UPDATE_FILTERS] ?: true,
        allowlist = this[Keys.ALLOWLIST] ?: emptySet(),
        newTabPage = enumOrDefault(this[Keys.NEW_TAB_PAGE], NewTabPage.START_PAGE),
        customHomeUrl = this[Keys.CUSTOM_HOME_URL] ?: "",
        startup = enumOrDefault(this[Keys.STARTUP], StartupBehavior.RESTORE_TABS),
        desktopByDefault = this[Keys.DESKTOP_DEFAULT] ?: false,
        showRecent = this[Keys.SHOW_RECENT] ?: true,
        lastFilterUpdate = this[Keys.LAST_FILTER_UPDATE] ?: 0L,
        shortcutsSeeded = this[Keys.SHORTCUTS_SEEDED] ?: false,
        textZoom = this[Keys.TEXT_ZOOM] ?: 100,
        httpsOnly = this[Keys.HTTPS_ONLY] ?: false,
        jsBlockedHosts = this[Keys.JS_BLOCKED] ?: emptySet(),
        appLock = this[Keys.APP_LOCK] ?: false,
        clearOnExit = this[Keys.CLEAR_ON_EXIT] ?: false,
        backgroundMedia = this[Keys.BACKGROUND_MEDIA] ?: false,
        prayerNotifications = this[Keys.PRAYER_NOTIFICATIONS] ?: true,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
