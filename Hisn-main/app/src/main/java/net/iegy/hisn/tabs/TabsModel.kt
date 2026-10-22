package net.iegy.hisn.tabs

enum class PageErrorKind { OFFLINE, DNS, TIMEOUT, CONNECTION, SSL, CRASH, GENERIC, HTTPS_ONLY }

/** A main-frame load failure, shown by the app's own error screen (with Retry) instead of the engine's default page. */
data class PageError(
    val kind: PageErrorKind,
    val url: String,
    val code: Int = 0,
    val detail: String? = null,
)

/** Everything the UI needs to know about a tab. Contains no Android types, so tab logic is unit-testable. */
data class TabState(
    val id: String,
    val isPrivate: Boolean = false,
    /** Empty URL means the tab shows the local start page and has no engine session. */
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false,
    val blockedCount: Int = 0,
    val error: PageError? = null,
    /** The tab that opened this one; selected again when this tab is closed. */
    val parentId: String? = null,
    /** Find-in-page progress: zero-based index of the highlighted match and the total number of matches. */
    val findIndex: Int = 0,
    val findCount: Int = 0,
    /** Optional label that groups tabs together in the tab switcher (kept only while the app runs). */
    val group: String? = null,
) {
    val isHome: Boolean get() = url.isBlank()
}

data class ClosedTab(val id: String, val url: String, val title: String, val desktopMode: Boolean)

/**
 * Immutable tab list + selection + recently-closed stack. All operations return a new model.
 * Private tabs are never added to the recently-closed stack.
 */
data class TabsModel(
    val tabs: List<TabState> = emptyList(),
    val selectedId: String? = null,
    val closed: List<ClosedTab> = emptyList(),
) {
    val selected: TabState? get() = tabs.firstOrNull { it.id == selectedId }

    fun add(tab: TabState, select: Boolean = true): TabsModel =
        copy(tabs = tabs + tab, selectedId = if (select || selectedId == null) tab.id else selectedId)

    fun select(id: String): TabsModel = if (tabs.any { it.id == id }) copy(selectedId = id) else this

    fun update(id: String, transform: (TabState) -> TabState): TabsModel =
        copy(tabs = tabs.map { if (it.id == id) transform(it) else it })

    fun close(id: String): TabsModel {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return this
        val tab = tabs[index]
        val remaining = tabs.filterNot { it.id == id }
        val newSelected = if (selectedId != id) {
            selectedId
        } else {
            // Prefer the opener, then the tab that slid into this slot, then the last tab.
            remaining.firstOrNull { it.id == tab.parentId }?.id
                ?: remaining.getOrNull(index)?.id
                ?: remaining.lastOrNull()?.id
        }
        return copy(tabs = remaining, selectedId = newSelected, closed = remember(closed, listOf(tab)))
    }

    fun closeOthers(id: String): TabsModel {
        val keep = tabs.firstOrNull { it.id == id } ?: return this
        val others = tabs.filterNot { it.id == id }
        return copy(tabs = listOf(keep), selectedId = keep.id, closed = remember(closed, others))
    }

    fun closeAll(): TabsModel = copy(tabs = emptyList(), selectedId = null, closed = remember(closed, tabs))

    /** Removes and returns the most recently closed tab, if any. */
    fun popClosed(): Pair<TabsModel, ClosedTab?> {
        val last = closed.firstOrNull() ?: return this to null
        return copy(closed = closed.drop(1)) to last
    }

    private fun remember(existing: List<ClosedTab>, closing: List<TabState>): List<ClosedTab> {
        val added = closing
            .filter { !it.isPrivate && !it.isHome }
            .map { ClosedTab(it.id, it.url, it.title, it.desktopMode) }
            .asReversed()
        return (added + existing).take(MAX_CLOSED)
    }

    companion object {
        const val MAX_CLOSED = 10
    }
}
