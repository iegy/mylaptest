package net.iegy.hisn.data

import kotlinx.coroutines.flow.Flow

class ShortcutsRepository(private val dao: ShortcutDao) {

    fun observe(): Flow<List<ShortcutEntity>> = dao.observeAll()

    suspend fun add(title: String, url: String) {
        val next = (dao.getAll().maxOfOrNull { it.position } ?: -1) + 1
        dao.insert(ShortcutEntity(title = title.ifBlank { url }.take(60), url = url, position = next))
    }

    suspend fun update(shortcut: ShortcutEntity, title: String, url: String) =
        dao.update(shortcut.copy(title = title.ifBlank { url }.take(60), url = url))

    suspend fun remove(id: Long) {
        dao.delete(id)
        renumber()
    }

    /** Moves a shortcut one step earlier (delta = -1) or later (delta = +1). */
    suspend fun move(id: Long, delta: Int) {
        val list = dao.getAll().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        dao.updateAll(list.mapIndexed { index, s -> s.copy(position = index) })
    }

    suspend fun seedDefaults() {
        if (dao.count() > 0) return
        dao.insertAll(
            DEFAULT_SHORTCUTS.mapIndexed { index, (title, url) ->
                ShortcutEntity(title = title, url = url, position = index)
            },
        )
    }

    private suspend fun renumber() {
        dao.updateAll(dao.getAll().mapIndexed { index, s -> s.copy(position = index) })
    }

    companion object {
        val DEFAULT_SHORTCUTS = listOf(
            "Google" to "https://www.google.com/",
            "YouTube" to "https://m.youtube.com/",
            "Wikipedia" to "https://www.wikipedia.org/",
            "DuckDuckGo" to "https://duckduckgo.com/",
        )
    }
}
