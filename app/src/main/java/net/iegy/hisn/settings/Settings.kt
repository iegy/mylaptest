package net.iegy.hisn.settings

import net.iegy.hisn.browser.SearchEngine

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ARABIC("ar"),
    ENGLISH("en"),
}

/** What a new tab shows. */
enum class NewTabPage { START_PAGE, CUSTOM_URL }

/** What the app shows when it is launched. */
enum class StartupBehavior { START_PAGE, RESTORE_TABS }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val blockingEnabled: Boolean = true,
    val autoUpdateFilters: Boolean = true,
    /** Normalised hosts on which blocking is disabled. */
    val allowlist: Set<String> = emptySet(),
    val newTabPage: NewTabPage = NewTabPage.START_PAGE,
    val customHomeUrl: String = "",
    val startup: StartupBehavior = StartupBehavior.RESTORE_TABS,
    val desktopByDefault: Boolean = false,
    val showRecent: Boolean = true,
    /** Epoch millis of the last successful filter-list update, 0 if never. */
    val lastFilterUpdate: Long = 0L,
    val shortcutsSeeded: Boolean = false,
    /** Page text size in percent (100 = normal). */
    val textZoom: Int = 100,
    /** Try https:// first for http links and warn before falling back to plain HTTP. */
    val httpsOnly: Boolean = false,
    /** Normalised hosts on which JavaScript is switched off. */
    val jsBlockedHosts: Set<String> = emptySet(),
    /** Ask for fingerprint / face / screen lock when the app is opened. */
    val appLock: Boolean = false,
    /** Wipe history and website data when the app is closed (and again at the next start, in case it was killed). */
    val clearOnExit: Boolean = false,
    /** Let audio / video keep playing when the app goes to the background. */
    val backgroundMedia: Boolean = false,
    /** Allow Rafiqi to schedule native prayer-time notifications. */
    val prayerNotifications: Boolean = true,
)
