package net.iegy.hisn.tabs

data class SavedTab(val id: String, val url: String, val title: String, val selected: Boolean)

/** Where regular-tab metadata is persisted. Private browsing is never given a store. */
interface TabsStore {
    suspend fun save(tabs: List<SavedTab>)
    suspend fun load(): List<SavedTab>
}
