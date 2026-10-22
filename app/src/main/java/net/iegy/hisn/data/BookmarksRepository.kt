package net.iegy.hisn.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarksRepository(private val dao: BookmarkDao) {

    /** Bookmarks directly inside [folderId] (null = root), or across everything when [query] is not blank. */
    fun observe(folderId: Long?, query: String): Flow<List<BookmarkEntity>> =
        if (query.isBlank()) dao.observeInFolder(folderId) else dao.search(query.trim())

    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observeBookmarkedUrls(): Flow<Set<String>> = dao.observeAll().map { list -> list.mapTo(HashSet()) { it.url } }

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun add(title: String, url: String, folderId: Long? = null, now: Long = System.currentTimeMillis()): Long =
        dao.insert(BookmarkEntity(title = title.ifBlank { url }.take(300), url = url, folderId = folderId, createdAt = now))

    /** Adds the bookmark if it does not exist, otherwise removes it. Returns true when it is bookmarked afterwards. */
    suspend fun toggle(url: String, title: String): Boolean {
        val existing = dao.findByUrl(url)
        return if (existing != null) {
            dao.deleteByUrl(url)
            false
        } else {
            add(title, url)
            true
        }
    }

    suspend fun update(id: Long, title: String, url: String, folderId: Long?, createdAt: Long) =
        dao.update(BookmarkEntity(id = id, title = title.ifBlank { url }.take(300), url = url, folderId = folderId, createdAt = createdAt))

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addFolder(name: String, now: Long = System.currentTimeMillis()): Long =
        dao.insertFolder(FolderEntity(name = name.trim().take(100), createdAt = now))

    suspend fun renameFolder(folder: FolderEntity, name: String) =
        dao.updateFolder(folder.copy(name = name.trim().take(100)))

    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)
}
