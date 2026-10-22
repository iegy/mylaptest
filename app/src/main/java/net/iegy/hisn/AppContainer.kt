package net.iegy.hisn

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.iegy.hisn.blocking.BlockingEngine
import net.iegy.hisn.blocking.FilterUpdate
import net.iegy.hisn.browser.BrowserEngine
import net.iegy.hisn.browser.WebViewEngine
import net.iegy.hisn.data.BackupManager
import net.iegy.hisn.data.BookmarksRepository
import net.iegy.hisn.data.DownloadsRepository
import net.iegy.hisn.data.HisnDatabase
import net.iegy.hisn.data.HistoryRepository
import net.iegy.hisn.data.ShortcutsRepository
import net.iegy.hisn.data.TabsRepository
import net.iegy.hisn.download.DownloadController
import net.iegy.hisn.settings.BootPrefs
import net.iegy.hisn.settings.Settings
import net.iegy.hisn.settings.SettingsRepository
import net.iegy.hisn.settings.StartupBehavior
import net.iegy.hisn.settings.hisnDataStore
import net.iegy.hisn.tabs.TabManager

/**
 * Hand-rolled dependency container, one per process.
 *
 * The regular process owns the database and DataStore. The private-browsing process (":private") deliberately
 * creates neither: it has no history, bookmarks, saved tabs or persisted settings, so there is no code path
 * that could write a private visit to disk. It receives a read-only settings snapshot from the main process.
 */
class AppContainer(val app: Application, val isPrivateProcess: Boolean) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val blocking = BlockingEngine(app)
    val engine: BrowserEngine = WebViewEngine(app, blocking)

    // ---- Regular process only (never touched when isPrivateProcess) -----------------------------------------
    private val database: HisnDatabase by lazy { HisnDatabase.create(app) }
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(app.hisnDataStore, BootPrefs(app)) }
    val bookmarksRepo: BookmarksRepository by lazy { BookmarksRepository(database.bookmarkDao()) }
    val historyRepo: HistoryRepository by lazy { HistoryRepository(database.historyDao(), scope) }
    val shortcutsRepo: ShortcutsRepository by lazy { ShortcutsRepository(database.shortcutDao()) }
    val downloadsRepo: DownloadsRepository by lazy { DownloadsRepository(database.downloadDao()) }
    val backup: BackupManager by lazy {
        BackupManager(database.bookmarkDao(), database.historyDao(), database.shortcutDao(), settingsRepo)
    }

    private val privateSettings = MutableStateFlow(BootPrefs.initialSettings(app))

    val settings: StateFlow<Settings> =
        if (isPrivateProcess) {
            privateSettings
        } else {
            settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, BootPrefs.initialSettings(app))
        }

    val downloads = DownloadController(app, if (isPrivateProcess) null else downloadsRepo, scope)

    val tabManager = TabManager(
        context = app,
        engine = engine,
        isPrivate = isPrivateProcess,
        scope = scope,
        settings = settings,
        history = if (isPrivateProcess) null else historyRepo,
        tabsStore = if (isPrivateProcess) null else TabsRepository(database.savedTabDao()),
        downloads = downloads,
    )

    init {
        scope.launch { blocking.load() }
        scope.launch { settings.collect { blocking.configure(it.blockingEnabled, it.allowlist) } }
        if (!isPrivateProcess) scope.launch { bootstrap() }
    }

    /** Private process only: applies the snapshot the main process sent when opening the private window. */
    fun applyPrivateSnapshot(snapshot: Settings) {
        privateSettings.value = snapshot
    }

    /** Private process only: per-site blocking changes live in memory for the session. */
    fun updatePrivateSettings(transform: (Settings) -> Settings) {
        privateSettings.value = transform(privateSettings.value)
    }

    /** Wipes history and website data. Used by "clear on exit", which runs on close and again at the next start. */
    suspend fun clearBrowsingData() {
        if (isPrivateProcess) return
        historyRepo.clear(net.iegy.hisn.data.ClearRange.ALL)
        engine.clearWebsiteData()
    }

    private suspend fun bootstrap() {
        val current = settingsRepo.settings.first()
        if (current.clearOnExit) clearBrowsingData()
        if (!current.shortcutsSeeded) {
            shortcutsRepo.seedDefaults()
            settingsRepo.setShortcutsSeeded()
        }
        tabManager.restore(current.startup == StartupBehavior.RESTORE_TABS)
        refreshFiltersIfDue(current)
    }

    /**
     * Weekly background refresh of the filter list. Runs once per launch, a while after startup, and never
     * while a page loads. A failure is silent and leaves the working rules in place.
     */
    private suspend fun refreshFiltersIfDue(current: Settings) {
        val now = System.currentTimeMillis()
        if (!current.autoUpdateFilters || now - current.lastFilterUpdate < FILTER_REFRESH_INTERVAL_MS) return
        delay(15_000)
        if (blocking.update() is FilterUpdate.Updated) {
            settingsRepo.setLastFilterUpdate(System.currentTimeMillis())
        }
    }

    private companion object {
        const val FILTER_REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
