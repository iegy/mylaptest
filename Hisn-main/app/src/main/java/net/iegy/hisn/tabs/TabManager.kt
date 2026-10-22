package net.iegy.hisn.tabs

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.iegy.hisn.R
import net.iegy.hisn.blocking.Allowlist
import net.iegy.hisn.browser.BrowserEngine
import net.iegy.hisn.browser.EngineSession
import net.iegy.hisn.browser.FileChooserRequest
import net.iegy.hisn.browser.HistoryPolicy
import net.iegy.hisn.browser.HistoryRecorder
import net.iegy.hisn.browser.LongPressTarget
import net.iegy.hisn.browser.RejectReason
import net.iegy.hisn.browser.ResolvedInput
import net.iegy.hisn.browser.SessionCallbacks
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.download.DownloadController
import net.iegy.hisn.download.DownloadStart
import net.iegy.hisn.settings.NewTabPage
import net.iegy.hisn.settings.Settings

private const val THUMBNAIL_WIDTH = 360

/** One-shot things the UI has to react to. */
sealed interface BrowserEvent {
    data class Message(@StringRes val res: Int, val arg: String? = null) : BrowserEvent
    data class ExternalLink(val url: String) : BrowserEvent
    data class WebPermission(
        val origin: String,
        val resources: List<String>,
        val grant: (List<String>) -> Unit,
        val deny: () -> Unit,
    ) : BrowserEvent
    data class FileChooser(val request: FileChooserRequest) : BrowserEvent
    data class LongPress(val tabId: String, val target: LongPressTarget) : BrowserEvent
    data class ShowCustomView(val view: View, val onHidden: () -> Unit) : BrowserEvent
    data object HideCustomView : BrowserEvent
}

/**
 * Owns every tab and the engine sessions behind them.
 *
 * Lifecycle strategy:
 *  - A tab's metadata ([TabState]) is cheap and always kept. Its engine session (a WebView) is created lazily the
 *    first time the tab is shown, so restored tabs cost nothing until opened.
 *  - At most [maxActive] sessions are alive. Creating another one freezes the least-recently-used session that is
 *    not selected: its navigation state is saved to a Bundle and the WebView is destroyed. Re-selecting the tab
 *    restores that state rather than reloading from scratch.
 *  - Switching tabs or opening the tab switcher never destroys anything; the view is merely detached/re-attached.
 *  - Closing a tab destroys its WebView and drops all state. Low-memory callbacks freeze everything except the
 *    selected tab.
 *
 * All methods must be called on the main thread (engine callbacks already are).
 */
class TabManager(
    private val context: Context,
    private val engine: BrowserEngine,
    val isPrivate: Boolean,
    private val scope: CoroutineScope,
    private val settings: StateFlow<Settings>,
    private val history: HistoryRecorder?,
    private val tabsStore: TabsStore?,
    private val downloads: DownloadController,
) {
    private val _model = MutableStateFlow(TabsModel())
    val model: StateFlow<TabsModel> = _model.asStateFlow()

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<BrowserEvent> = _events.asSharedFlow()

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    /** In-memory page snapshots for the tab switcher. Never written to disk, private tabs included. */
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    // HTTPS-only mode: hosts the user chose to open over plain HTTP (this run only) and the http address each tab
    // is currently trying to reach through https, so a failed upgrade can offer "continue with HTTP".
    private val httpAllowed = HashSet<String>()
    private val httpFallback = HashMap<String, String>()

    // Access-ordered: iteration order is least- to most-recently used.
    private val sessions = LinkedHashMap<String, EngineSession>(16, 0.75f, true)
    private val frozen = HashMap<String, Bundle>()
    private var activity: Activity? = null
    private var persistJob: Job? = null

    private val maxActive: Int = run {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) 2 else 5
    }

    // ---- Tab operations -------------------------------------------------------------------------------------

    /** Opens a tab showing the start page (or [url] if given). Returns the new tab's id. */
    fun newTab(url: String = "", select: Boolean = true, parentId: String? = null): String {
        val id = UUID.randomUUID().toString()
        val tab = TabState(
            id = id,
            isPrivate = isPrivate,
            desktopMode = settings.value.desktopByDefault,
            parentId = parentId,
        )
        _model.update { it.add(tab, select) }
        if (url.isNotBlank()) navigate(id, url)
        persist()
        return id
    }

    /** New tab using the user's home-page preference. */
    fun openNewTab(): String {
        val s = settings.value
        val custom = if (s.newTabPage == NewTabPage.CUSTOM_URL) UrlResolver.normalizeWebUrl(s.customHomeUrl) else null
        return newTab(url = custom.orEmpty())
    }

    fun openLinkInNewTab(url: String, select: Boolean = true, parentId: String? = null) {
        val safe = UrlResolver.normalizeWebUrl(url) ?: return
        newTab(url = safe, select = select, parentId = parentId)
    }

    fun select(id: String) {
        _model.update { it.select(id) }
        applyLifecycle()
        persist()
    }

    fun close(id: String) {
        sessions.remove(id)?.destroy()
        frozen.remove(id)
        dropTabExtras(id)
        _model.update { it.close(id) }
        afterClosing()
    }

    fun closeOthers(id: String) {
        _model.value.tabs.filter { it.id != id }.forEach { releaseSession(it.id); dropTabExtras(it.id) }
        _model.update { it.closeOthers(id) }
        afterClosing()
    }

    fun closeAll() {
        _model.value.tabs.forEach { releaseSession(it.id); dropTabExtras(it.id) }
        _model.update { it.closeAll() }
        afterClosing()
    }

    fun reopenClosed(): Boolean {
        val (next, closed) = _model.value.popClosed()
        if (closed == null) return false
        _model.value = next
        val id = newTab(select = true)
        _model.update { m -> m.update(id) { it.copy(desktopMode = closed.desktopMode) } }
        navigate(id, closed.url)
        return true
    }

    private fun afterClosing() {
        // A regular browser always has a tab. A private session with no tabs is ended by the UI.
        if (!isPrivate && _model.value.tabs.isEmpty()) newTab()
        applyLifecycle()
        persist()
    }

    private fun dropTabExtras(id: String) {
        httpFallback.remove(id)
        _thumbnails.update { it - id }
    }

    private fun releaseSession(id: String) {
        sessions.remove(id)?.destroy()
        frozen.remove(id)
    }

    // ---- Navigation -----------------------------------------------------------------------------------------

    fun navigate(id: String, input: String) {
        when (val resolved = UrlResolver.resolve(input, settings.value.searchEngine)) {
            is ResolvedInput.Navigate -> load(id, resolved.url)
            is ResolvedInput.Search -> load(id, resolved.url)
            is ResolvedInput.External -> _events.tryEmit(BrowserEvent.ExternalLink(resolved.url))
            is ResolvedInput.Rejected ->
                if (resolved.reason != RejectReason.EMPTY) {
                    _events.tryEmit(BrowserEvent.Message(R.string.msg_unsupported_address))
                }
        }
    }

    private fun load(id: String, requested: String) {
        if (_model.value.tabs.none { it.id == id }) return
        frozen.remove(id)
        httpFallback.remove(id)
        val url = upgrade(id, requested) ?: requested
        _model.update { m ->
            m.update(id) {
                it.copy(url = url, title = "", error = null, isLoading = true, progress = 0, blockedCount = 0)
            }
        }
        // autoLoad=false: the explicit load below is the only navigation (no double load of a fresh session).
        sessionFor(id, autoLoad = false)?.load(url)
        persist()
    }

    fun reload(id: String) {
        val tab = tabOf(id) ?: return
        if (tab.error != null) retry(id) else sessions[id]?.reload()
    }

    fun stop(id: String) {
        sessions[id]?.stop()
        _model.update { m -> m.update(id) { it.copy(isLoading = false) } }
    }

    fun goBack(id: String) {
        sessions[id]?.goBack()
    }

    fun goForward(id: String) {
        sessions[id]?.goForward()
    }

    /** Back to the local start page in this tab, dropping its page session. */
    fun goHome(id: String) {
        releaseSession(id)
        _model.update { m ->
            m.update(id) {
                it.copy(
                    url = "", title = "", error = null, isLoading = false, progress = 0,
                    canGoBack = false, canGoForward = false, blockedCount = 0,
                )
            }
        }
        persist()
    }

    // ---- HTTPS-only mode ------------------------------------------------------------------------------------

    /** Returns the https:// twin of an http:// address when HTTPS-only mode applies to it, otherwise null. */
    private fun upgrade(id: String, url: String): String? {
        if (!settings.value.httpsOnly || UrlResolver.schemeOf(url) != "http") return null
        val host = UrlResolver.hostOf(url) ?: return null
        if (host in httpAllowed || isLocalHost(host)) return null
        httpFallback[id] = url
        return url.replaceFirst(Regex("^http", RegexOption.IGNORE_CASE), "https")
    }

    private fun isLocalHost(host: String): Boolean =
        host == "localhost" || host.endsWith(".local") || host.contains(':') ||
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)

    /** The user accepted the warning: open this tab's site over plain HTTP (until the app is closed). */
    fun allowHttp(id: String) {
        val error = tabOf(id)?.error ?: return
        if (error.kind != PageErrorKind.HTTPS_ONLY) return
        UrlResolver.hostOf(error.url)?.let { httpAllowed += it }
        releaseSession(id)
        load(id, error.url)
    }

    // ---- Page tools: zoom, print, thumbnails, groups ---------------------------------------------------------

    fun setTextZoom(percent: Int) {
        sessions.values.forEach { it.setTextZoom(percent) }
    }

    fun printPage(id: String) {
        sessions[id]?.print(tabOf(id)?.title.orEmpty())
    }

    fun captureThumbnail(id: String) {
        val bitmap = sessions[id]?.captureThumbnail(THUMBNAIL_WIDTH) ?: return
        _thumbnails.update { it + (id to bitmap) }
    }

    /** Full-resolution picture of the page (visible part, or all of it), taken on the main thread; saving is up to the caller. */
    fun captureScreenshot(id: String, fullPage: Boolean, onResult: (net.iegy.hisn.browser.PageScreenshot?) -> Unit) {
        val session = sessions[id]
        if (session == null) onResult(null) else session.captureScreenshot(fullPage, onResult)
    }

    fun setGroup(id: String, group: String?) {
        val clean = group?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
        _model.update { m -> m.update(id) { it.copy(group = clean) } }
    }

    // ---- Find in page & reader ------------------------------------------------------------------------------

    fun findInPage(id: String, query: String) {
        sessions[id]?.findAll(query)
    }

    fun findNext(id: String, forward: Boolean) {
        sessions[id]?.findNext(forward)
    }

    fun clearFind(id: String) {
        sessions[id]?.clearFind()
        _model.update { m -> m.update(id) { it.copy(findIndex = 0, findCount = 0) } }
    }

    fun toggleReader(id: String) {
        sessions[id]?.toggleReader()
    }

    fun retry(id: String) {
        val error = tabOf(id)?.error ?: return reload(id)
        releaseSession(id)
        load(id, error.url)
    }

    fun setDesktopMode(id: String, enabled: Boolean) {
        _model.update { m -> m.update(id) { it.copy(desktopMode = enabled) } }
        sessions[id]?.setDesktopMode(enabled)
    }

    /**
     * The view to display for [id], created on demand. Null for start-page tabs and for tabs whose renderer
     * crashed (the UI shows the error screen with Retry instead of silently recreating a crashing page).
     */
    fun viewFor(id: String): View? {
        val tab = tabOf(id) ?: return null
        if (tab.isHome || tab.error?.kind == PageErrorKind.CRASH) return null
        return sessionFor(id)?.view
    }

    /**
     * Discovers downloadable media for the current page of [id].
     * Combines DOM inspection (JS) with media URLs observed during load.
     * [onResult] is always invoked on the main thread.
     */
    fun findMediaCandidates(id: String, onResult: (List<net.iegy.hisn.browser.MediaCandidate>) -> Unit) {
        val tab = tabOf(id)
        if (tab == null || tab.isHome) {
            onResult(emptyList())
            return
        }
        // Direct file URL opened as the page itself.
        if (net.iegy.hisn.browser.MediaDownload.isDownloadableMediaUrl(tab.url)) {
            onResult(
                listOf(
                    net.iegy.hisn.browser.MediaCandidate(
                        url = tab.url,
                        label = "Current file",
                        mimeHint = null,
                    ),
                ),
            )
            return
        }
        val session = sessionFor(id) ?: run {
            onResult(emptyList())
            return
        }
        val observed = session.observedMediaUrls()
        session.evaluateJavascript(net.iegy.hisn.browser.MediaDownload.FIND_STREAMS_JS) { json ->
            val fromPage = net.iegy.hisn.browser.MediaDownload.parseJsResult(json)
            val merged = net.iegy.hisn.browser.MediaDownload.mergeCandidates(fromPage, observed, tab.url)
            onResult(merged)
        }
    }

    private fun sessionFor(id: String, autoLoad: Boolean = true): EngineSession? {
        sessions[id]?.let { return it }
        val tab = tabOf(id) ?: return null
        evictIfNeeded(keep = setOf(id))
        val session = engine.createSession(TabCallbacks(id), tab.desktopMode)
        session.attachActivity(activity)
        sessions[id] = session
        if (autoLoad) {
            val saved = frozen.remove(id)
            if (saved == null || !session.restoreState(saved)) {
                if (tab.url.isNotBlank() && tab.url != "about:blank") session.load(tab.url)
            }
        }
        applyLifecycle()
        return session
    }

    // ---- Memory & lifecycle ---------------------------------------------------------------------------------

    private fun evictIfNeeded(keep: Set<String>) {
        val selected = _model.value.selectedId
        while (sessions.size >= maxActive) {
            val victim = sessions.keys.firstOrNull { it !in keep && it != selected } ?: return
            freeze(victim)
        }
    }

    private fun freeze(id: String) {
        val session = sessions.remove(id) ?: return
        session.saveState()?.let { frozen[id] = it }
        session.destroy()
    }

    /** Called on low-memory signals: keep only the visible tab's WebView alive. */
    fun onLowMemory() {
        val selected = _model.value.selectedId
        sessions.keys.filter { it != selected }.forEach { freeze(it) }
    }

    fun attachActivity(activity: Activity?) {
        this.activity = activity
        sessions.values.forEach { it.attachActivity(activity) }
    }

    fun onHostPaused() {
        // With "keep playing in the background" the pages are left running; everything else is paused to save battery.
        if (!settings.value.backgroundMedia) {
            sessions.values.forEach { it.onPause() }
            engine.pauseAll()
        }
        flushPersistNow()
    }

    fun onHostResumed() {
        engine.resumeAll()
        applyLifecycle()
    }

    private fun applyLifecycle() {
        val selected = _model.value.selectedId
        for ((id, session) in sessions) {
            if (id == selected) session.onResume() else session.onPause()
        }
    }

    /** Ends a private session: every WebView is destroyed and cookies, storage and cache are wiped. */
    suspend fun clearAllSessionData() {
        sessions.keys.toList().forEach { releaseSession(it) }
        frozen.clear()
        _model.value = TabsModel()
        _thumbnails.value = emptyMap()
        httpFallback.clear()
        engine.clearWebsiteData()
    }

    // ---- Persistence (regular tabs only) --------------------------------------------------------------------

    suspend fun restore(restoreTabs: Boolean) {
        val saved = if (restoreTabs && !isPrivate) tabsStore?.load().orEmpty() else emptyList()
        if (saved.isEmpty()) {
            if (_model.value.tabs.isEmpty()) openNewTab()
            return
        }
        var model = TabsModel()
        for (t in saved) {
            model = model.add(
                TabState(id = t.id, url = t.url, title = t.title, desktopMode = settings.value.desktopByDefault),
                select = t.selected,
            )
        }
        _model.value = if (model.selectedId == null) model.select(model.tabs.last().id) else model
    }

    private fun persist() {
        val store = tabsStore ?: return
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(500)
            store.save(snapshotForStore())
        }
    }

    private fun flushPersistNow() {
        val store = tabsStore ?: return
        persistJob?.cancel()
        val snapshot = snapshotForStore()
        scope.launch { store.save(snapshot) }
    }

    /** Open tab URLs are only kept on disk when the user chose "restore tabs on startup"; otherwise nothing is. */
    private fun snapshotForStore(): List<SavedTab> {
        if (settings.value.startup != net.iegy.hisn.settings.StartupBehavior.RESTORE_TABS) return emptyList()
        val m = _model.value
        return m.tabs.map { SavedTab(it.id, it.url, it.title, it.id == m.selectedId) }
    }

    // ---- Helpers --------------------------------------------------------------------------------------------

    private fun tabOf(id: String): TabState? = _model.value.tabs.firstOrNull { it.id == id }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Engine -> tab bridge. Ignores callbacks from sessions that have since been destroyed. */
    private inner class TabCallbacks(private val id: String) : SessionCallbacks {
        private fun alive() = sessions.containsKey(id)
        private fun update(transform: (TabState) -> TabState) = _model.update { it.update(id, transform) }

        override val isPrivateSession: Boolean get() = isPrivate

        override fun currentTextZoom(): Int = settings.value.textZoom

        override fun isJavaScriptAllowed(url: String): Boolean {
            val host = UrlResolver.hostOf(url) ?: return true
            return !Allowlist.isAllowed(settings.value.jsBlockedHosts, host)
        }

        override fun upgradeUrl(url: String): String? = upgrade(id, url)

        override fun onPageStarted(url: String) {
            if (!alive()) return
            update { it.copy(url = url, isLoading = true, progress = 0, error = null) }
        }

        override fun onPageFinished(url: String, title: String, hadError: Boolean) {
            if (!alive()) return
            update { it.copy(isLoading = false, progress = 100, title = title.ifBlank { it.title }) }
            if (!hadError) httpFallback.remove(id)
            if (HistoryPolicy.shouldRecord(isPrivate, url, hadError)) {
                history?.record(url, title)
            }
            persist()
        }

        override fun onUrlChanged(url: String) {
            if (alive()) update { it.copy(url = url) }
        }

        override fun onTitleChanged(title: String) {
            if (alive()) update { it.copy(title = title) }
        }

        override fun onProgress(progress: Int) {
            if (alive()) update { it.copy(progress = progress, isLoading = progress < 100) }
        }

        override fun onNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
            if (alive()) update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
        }

        override fun onError(error: PageError) {
            if (!alive()) return
            val offline = !isOnline() && error.kind != PageErrorKind.SSL
            val httpUrl = httpFallback[id]
            val shown = when {
                offline -> error.copy(kind = PageErrorKind.OFFLINE)
                // The https:// twin of an http link failed: let the user decide whether plain HTTP is acceptable.
                httpUrl != null -> PageError(PageErrorKind.HTTPS_ONLY, httpUrl, error.code, error.detail)
                else -> error
            }
            update { it.copy(error = shown, isLoading = false) }
        }

        override fun onBlockedCount(count: Int) {
            if (alive()) update { it.copy(blockedCount = count) }
        }

        override fun onCreateWindow(): EngineSession? {
            if (!alive()) return null
            val newId = newTab(select = true, parentId = id)
            // "about:blank" marks the tab as a page tab (not the start page) until the popup's real URL arrives.
            _model.update { m -> m.update(newId) { it.copy(url = "about:blank") } }
            sessionFor(newId)
            return sessions[newId]
        }

        override fun onCloseWindow() {
            if (alive()) close(id)
        }

        override fun onExternalUrl(url: String) {
            _events.tryEmit(BrowserEvent.ExternalLink(url))
        }

        override fun onDownload(
            url: String,
            userAgent: String?,
            contentDisposition: String?,
            mimeType: String?,
            contentLength: Long,
        ) {
            val referer = tabOf(id)?.url
            when (val result = downloads.enqueue(url, userAgent, contentDisposition, mimeType, contentLength, referer)) {
                is DownloadStart.Started -> _events.tryEmit(BrowserEvent.Message(R.string.msg_download_started, result.fileName))
                is DownloadStart.Failed -> _events.tryEmit(BrowserEvent.Message(result.messageRes))
            }
        }

        override fun onLongPress(target: LongPressTarget) {
            _events.tryEmit(BrowserEvent.LongPress(id, target))
        }

        override fun onFileChooser(request: FileChooserRequest) {
            _events.tryEmit(BrowserEvent.FileChooser(request))
        }

        override fun onWebPermission(
            origin: String,
            resources: List<String>,
            grant: (List<String>) -> Unit,
            deny: () -> Unit,
        ) {
            _events.tryEmit(BrowserEvent.WebPermission(origin, resources, grant, deny))
        }

        override fun onShowCustomView(view: View, onHidden: () -> Unit) {
            _events.tryEmit(BrowserEvent.ShowCustomView(view, onHidden))
        }

        override fun onHideCustomView() {
            _events.tryEmit(BrowserEvent.HideCustomView)
        }

        override fun onFindResult(activeIndex: Int, total: Int) {
            if (!alive()) return
            update { it.copy(findIndex = activeIndex, findCount = total) }
        }

        override fun onRenderProcessGone(didCrash: Boolean) {
            if (!alive()) return
            val url = tabOf(id)?.url.orEmpty()
            sessions.remove(id)?.destroy()
            frozen.remove(id)
            update { it.copy(error = PageError(PageErrorKind.CRASH, url), isLoading = false) }
        }
    }
}
