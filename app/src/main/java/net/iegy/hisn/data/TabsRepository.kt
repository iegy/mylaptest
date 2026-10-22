package net.iegy.hisn.data

import net.iegy.hisn.tabs.SavedTab
import net.iegy.hisn.tabs.TabsStore

/** Persists regular-tab metadata (never private tabs) so tabs can be restored on the next launch. */
class TabsRepository(private val dao: SavedTabDao) : TabsStore {

    override suspend fun save(tabs: List<SavedTab>) {
        dao.replaceAll(
            tabs.mapIndexed { index, t ->
                SavedTabEntity(id = t.id, url = t.url, title = t.title, position = index, selected = t.selected)
            },
        )
    }

    override suspend fun load(): List<SavedTab> =
        dao.getAll().map { SavedTab(id = it.id, url = it.url, title = it.title, selected = it.selected) }
}
