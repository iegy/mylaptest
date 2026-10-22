package net.iegy.hisn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookmarkDao {
    @Query(
        "SELECT * FROM bookmarks WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId " +
            "ORDER BY title COLLATE NOCASE",
    )
    abstract fun observeInFolder(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY title COLLATE NOCASE",
    )
    abstract fun search(query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    abstract suspend fun getAll(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    abstract suspend fun findByUrl(url: String): BookmarkEntity?

    @Insert
    abstract suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    abstract suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    abstract suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmark_folders ORDER BY name COLLATE NOCASE")
    abstract fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM bookmark_folders")
    abstract suspend fun getFolders(): List<FolderEntity>

    @Insert
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    abstract suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    abstract suspend fun deleteFolderRow(id: Long)

    @Query("UPDATE bookmarks SET folderId = NULL WHERE folderId = :id")
    abstract suspend fun moveFolderContentsToRoot(id: Long)

    /** Deleting a folder keeps its bookmarks: they move to the root level. */
    @Transaction
    open suspend fun deleteFolder(id: Long) {
        moveFolderContentsToRoot(id)
        deleteFolderRow(id)
    }
}

@Dao
abstract class HistoryDao {
    @Insert
    abstract suspend fun insert(entry: HistoryEntity): Long

    @Insert
    abstract suspend fun insertAll(entries: List<HistoryEntity>)

    @Query(
        "SELECT * FROM history WHERE :query = '' OR title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY visitedAt DESC LIMIT 1000",
    )
    abstract fun observe(query: String): Flow<List<HistoryEntity>>

    /** Most recent visit per URL, for the start page's "recently visited" section. */
    @Query(
        "SELECT * FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY url) " +
            "ORDER BY visitedAt DESC LIMIT :limit",
    )
    abstract fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    abstract suspend fun getAll(): List<HistoryEntity>

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :since")
    abstract suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    abstract suspend fun deleteAll()
}

@Dao
abstract class ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position")
    abstract fun observeAll(): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM shortcuts ORDER BY position")
    abstract suspend fun getAll(): List<ShortcutEntity>

    @Insert
    abstract suspend fun insert(shortcut: ShortcutEntity): Long

    @Insert
    abstract suspend fun insertAll(shortcuts: List<ShortcutEntity>)

    @Update
    abstract suspend fun update(shortcut: ShortcutEntity)

    /** Room runs a list update inside a single transaction, so reordering is atomic. */
    @Update
    abstract suspend fun updateAll(shortcuts: List<ShortcutEntity>)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM shortcuts")
    abstract suspend fun count(): Int
}

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    abstract fun observeAll(): Flow<List<DownloadEntity>>

    @Insert
    abstract suspend fun insert(download: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM downloads")
    abstract suspend fun getAll(): List<DownloadEntity>
}

@Dao
abstract class SavedTabDao {
    @Query("SELECT * FROM saved_tabs ORDER BY position")
    abstract suspend fun getAll(): List<SavedTabEntity>

    @Query("DELETE FROM saved_tabs")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(tabs: List<SavedTabEntity>)

    @Transaction
    open suspend fun replaceAll(tabs: List<SavedTabEntity>) {
        deleteAll()
        insertAll(tabs)
    }
}
