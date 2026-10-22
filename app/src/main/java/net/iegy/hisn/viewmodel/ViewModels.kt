package net.iegy.hisn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.iegy.hisn.AppContainer
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.blocking.FilterUpdate
import net.iegy.hisn.browser.SearchEngine
import net.iegy.hisn.data.BookmarkEntity
import net.iegy.hisn.data.ClearRange
import net.iegy.hisn.data.FolderEntity
import net.iegy.hisn.data.HistoryEntity
import net.iegy.hisn.data.ShortcutEntity
import net.iegy.hisn.data.ShortcutsRepository
import net.iegy.hisn.download.DownloadItem
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.settings.AppLanguage
import net.iegy.hisn.settings.NewTabPage
import net.iegy.hisn.settings.Settings
import net.iegy.hisn.settings.StartupBehavior
import net.iegy.hisn.settings.ThemeMode
import net.iegy.hisn.tabs.TabManager
import net.iegy.hisn.tabs.TabsModel

class HisnViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(BrowserViewModel::class.java) -> BrowserViewModel(container)
        modelClass.isAssignableFrom(BookmarksViewModel::class.java) -> BookmarksViewModel(container)
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(container)
        modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> DownloadsViewModel(container)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}

/** One row in the address bar's suggestion list. */
data class Suggestion(val title: String, val url: String, val isBookmark: Boolean)

/** Start page, address bar, tabs and per-site blocking. Works in both the regular and the private process. */
class BrowserViewModel(private val c: AppContainer) : ViewModel() {
    val isPrivate: Boolean = c.isPrivateProcess
    val tabs: TabManager = c.tabManager
    val model: StateFlow<TabsModel> = c.tabManager.model
    val settings: StateFlow<Settings> = c.settings
    val ruleCount: StateFlow<Int> = c.blocking.ruleCount

    // The private process has no database: it shows the default shortcuts and nothing user-specific.
    val shortcuts: StateFlow<List<ShortcutEntity>> =
        if (isPrivate) {
            MutableStateFlow(
                ShortcutsRepository.DEFAULT_SHORTCUTS.mapIndexed { i, (title, url) ->
                    ShortcutEntity(id = -(i + 1L), title = title, url = url, position = i)
                },
            )
        } else {
            c.shortcutsRepo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    val recent: StateFlow<List<HistoryEntity>> =
        if (isPrivate) MutableStateFlow(emptyList())
        else c.historyRepo.observeRecent(6).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeBookmarks: StateFlow<List<BookmarkEntity>> =
        if (isPrivate) MutableStateFlow(emptyList())
        else c.bookmarksRepo.observeAll().map { it.take(8) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarkedUrls: StateFlow<Set<String>> =
        if (isPrivate) MutableStateFlow(emptySet())
        else c.bookmarksRepo.observeBookmarkedUrls()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Text currently typed in the address bar (empty when not editing). */
    val suggestionQuery = MutableStateFlow("")

    /** Local-only suggestions from bookmarks and history. The private window has no database, so it gets none. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<Suggestion>> =
        if (isPrivate) {
            MutableStateFlow(emptyList())
        } else {
            suggestionQuery.debounce(120).flatMapLatest { q ->
                val text = q.trim()
                if (text.length < 2) {
                    flowOf(emptyList<Suggestion>())
                } else {
                    combine(c.bookmarksRepo.observe(null, text), c.historyRepo.observe(text)) { marks, visits ->
                        val fromBookmarks = marks.take(3).map { m -> Suggestion(m.title.ifBlank { m.url }, m.url, true) }
                        val seen = fromBookmarks.mapTo(HashSet()) { s -> s.url }
                        val fromHistory = visits.asSequence()
                            .filter { h -> h.url !in seen }
                            .distinctBy { h -> h.url }
                            .take(5)
                            .map { h -> Suggestion(h.title.ifBlank { h.url }, h.url, false) }
                            .toList()
                        fromBookmarks + fromHistory
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun toggleBookmark(url: String, title: String) {
        if (isPrivate) return
        viewModelScope.launch { c.bookmarksRepo.toggle(url, title) }
    }

    fun setShowRecent(value: Boolean) {
        if (!isPrivate) viewModelScope.launch { c.settingsRepo.setShowRecent(value) }
    }

    fun clearAllHistory() {
        if (!isPrivate) viewModelScope.launch { c.historyRepo.clear(ClearRange.ALL) }
    }

    fun addShortcut(title: String, url: String) {
        if (!isPrivate) viewModelScope.launch { c.shortcutsRepo.add(title, url) }
    }

    fun updateShortcut(shortcut: ShortcutEntity, title: String, url: String) {
        if (!isPrivate) viewModelScope.launch { c.shortcutsRepo.update(shortcut, title, url) }
    }

    fun removeShortcut(id: Long) {
        if (!isPrivate) viewModelScope.launch { c.shortcutsRepo.remove(id) }
    }

    fun moveShortcut(id: Long, delta: Int) {
        if (!isPrivate) viewModelScope.launch { c.shortcutsRepo.move(id, delta) }
    }

    /** Turns blocking off/on for one site. Regular: saved. Private: kept for the session only. */
    fun setSiteBlocking(host: String, blockingOn: Boolean) {
        val normalized = Allowlist.normalize(host)
        if (isPrivate) {
            c.updatePrivateSettings { s ->
                s.copy(allowlist = if (blockingOn) s.allowlist - normalized else s.allowlist + normalized)
            }
        } else {
            viewModelScope.launch {
                if (blockingOn) c.settingsRepo.removeFromAllowlist(normalized) else c.settingsRepo.addToAllowlist(normalized)
            }
        }
    }

    /** Switches JavaScript off/on for one site. Regular: saved. Private: kept for the session only. */
    fun setSiteJavaScript(host: String, enabled: Boolean) {
        val normalized = Allowlist.normalize(host)
        if (isPrivate) {
            c.updatePrivateSettings { s ->
                s.copy(jsBlockedHosts = if (enabled) s.jsBlockedHosts - normalized else s.jsBlockedHosts + normalized)
            }
        } else {
            viewModelScope.launch {
                if (enabled) c.settingsRepo.removeJsBlocked(normalized) else c.settingsRepo.addJsBlocked(normalized)
            }
        }
    }

    fun enqueueDownload(url: String, referer: String?): DownloadStart =
        c.downloads.enqueue(url, null, null, null, -1, referer)
}

class BookmarksViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.bookmarksRepo
    val folderId = MutableStateFlow<Long?>(null)
    val query = MutableStateFlow("")
    val folders: StateFlow<List<FolderEntity>> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        combine(folderId, query) { f, q -> f to q }
            .flatMapLatest { (f, q) -> repo.observe(f, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(existing: BookmarkEntity?, title: String, url: String, folder: Long?) {
        viewModelScope.launch {
            if (existing == null) repo.add(title, url, folder)
            else repo.update(existing.id, title, url, folder, existing.createdAt)
        }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }.let { }
    fun addFolder(name: String) = viewModelScope.launch { repo.addFolder(name) }.let { }
    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch { repo.renameFolder(folder, name) }.let { }
    fun deleteFolder(id: Long) {
        viewModelScope.launch { repo.deleteFolder(id) }
        if (folderId.value == id) folderId.value = null
    }
}

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.historyRepo
    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<HistoryEntity>> =
        query.flatMapLatest { repo.observe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }.let { }
    fun clear(range: ClearRange) = viewModelScope.launch { repo.clear(range) }.let { }
}

class DownloadsViewModel(private val c: AppContainer) : ViewModel() {
    private val controller = c.downloads

    /**
     * Live list: the saved rows joined with DownloadManager's current state. While anything is still running the
     * state is re-read every second (a cheap local query); once everything is settled it stops polling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<DownloadItem>> =
        c.downloadsRepo.observe()
            .flatMapLatest { rows ->
                flow {
                    while (true) {
                        val snapshot = withContext(Dispatchers.IO) { controller.snapshot(rows) }
                        emit(snapshot)
                        if (snapshot.none { it.isActive }) break
                        delay(1_000)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun contentUri(item: DownloadItem) = controller.contentUri(item)
    fun retry(item: DownloadItem): DownloadStart = controller.retry(item)
    fun remove(item: DownloadItem, deleteFile: Boolean) = controller.remove(item, deleteFile)
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.settingsRepo
    val settings: StateFlow<Settings> = c.settings
    val ruleCount: StateFlow<Int> = c.blocking.ruleCount
    private val _updating = MutableStateFlow(false)
    val updatingFilters: StateFlow<Boolean> = _updating.asStateFlow()

    fun setTheme(v: ThemeMode) = viewModelScope.launch { repo.setTheme(v) }.let { }
    fun setLanguage(v: AppLanguage, onApplied: () -> Unit) {
        viewModelScope.launch {
            repo.setLanguage(v)
            onApplied()
        }
    }
    fun setSearchEngine(v: SearchEngine) = viewModelScope.launch { repo.setSearchEngine(v) }.let { }
    fun setBlocking(v: Boolean) = viewModelScope.launch { repo.setBlockingEnabled(v) }.let { }
    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { repo.setAutoUpdateFilters(v) }.let { }
    fun setNewTabPage(v: NewTabPage) = viewModelScope.launch { repo.setNewTabPage(v) }.let { }
    fun setCustomHome(v: String) = viewModelScope.launch { repo.setCustomHomeUrl(v) }.let { }
    fun setStartup(v: StartupBehavior) = viewModelScope.launch { repo.setStartup(v) }.let { }
    fun setDesktopDefault(v: Boolean) = viewModelScope.launch { repo.setDesktopByDefault(v) }.let { }
    fun setShowRecent(v: Boolean) = viewModelScope.launch { repo.setShowRecent(v) }.let { }
    fun setTextZoom(v: Int) {
        viewModelScope.launch {
            repo.setTextZoom(v)
            c.tabManager.setTextZoom(v)
        }
    }
    fun setHttpsOnly(v: Boolean) = viewModelScope.launch { repo.setHttpsOnly(v) }.let { }
    fun setAppLock(v: Boolean) = viewModelScope.launch { repo.setAppLock(v) }.let { }
    fun setClearOnExit(v: Boolean) = viewModelScope.launch { repo.setClearOnExit(v) }.let { }
    fun setBackgroundMedia(v: Boolean) = viewModelScope.launch { repo.setBackgroundMedia(v) }.let { }
    fun setPrayerNotifications(v: Boolean) = viewModelScope.launch {
        repo.setPrayerNotifications(v)
        net.iegy.hisn.rafiqi.PrayerScheduler(c.app).setEnabled(v)
    }.let { }
    fun addAllowed(host: String) = viewModelScope.launch { repo.addToAllowlist(host) }.let { }
    fun removeAllowed(host: String) = viewModelScope.launch { repo.removeFromAllowlist(host) }.let { }
    fun clearHistory(range: ClearRange) = viewModelScope.launch { c.historyRepo.clear(range) }.let { }

    fun clearWebsiteData(onDone: () -> Unit) {
        viewModelScope.launch {
            c.engine.clearWebsiteData()
            onDone()
        }
    }

    fun updateFilters(onResult: (Boolean) -> Unit) {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            val ok = c.blocking.update() is FilterUpdate.Updated
            if (ok) repo.setLastFilterUpdate(System.currentTimeMillis())
            _updating.value = false
            onResult(ok)
        }
    }
}
