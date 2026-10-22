package net.iegy.hisn.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarksAndHistoryTest {
    private lateinit var context: Context
    private lateinit var db: HisnDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HisnDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun bookmarkToggleAddsThenRemoves() = runBlocking {
        val repo = BookmarksRepository(db.bookmarkDao())
        assertTrue(repo.toggle("https://example.com", "Example"))
        assertEquals(setOf("https://example.com"), repo.observeBookmarkedUrls().first())
        assertFalse(repo.toggle("https://example.com", "Example"))
        assertTrue(repo.observeBookmarkedUrls().first().isEmpty())
    }

    @Test fun bookmarksSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val name = "persist-test.db"
        context.deleteDatabase(name)
        val first = HisnDatabase.create(context, name)
        BookmarksRepository(first.bookmarkDao()).add("Example", "https://example.com")
        first.close()

        val second = HisnDatabase.create(context, name)
        val stored = BookmarksRepository(second.bookmarkDao()).observeAll().first()
        second.close()
        context.deleteDatabase(name)

        assertEquals(listOf("https://example.com"), stored.map { it.url })
    }

    @Test fun foldersGroupBookmarksAndDeletingAFolderKeepsItsBookmarks() = runBlocking {
        val repo = BookmarksRepository(db.bookmarkDao())
        val folder = repo.addFolder("News")
        repo.add("A", "https://a.com", folder)
        repo.add("B", "https://b.com", null)
        assertEquals(listOf("A"), repo.observe(folder, "").first().map { it.title })
        assertEquals(listOf("B"), repo.observe(null, "").first().map { it.title })

        repo.deleteFolder(folder)
        assertEquals(setOf("A", "B"), repo.observe(null, "").first().map { it.title }.toSet())
    }

    @Test fun bookmarkSearchMatchesTitleAndUrl() = runBlocking {
        val repo = BookmarksRepository(db.bookmarkDao())
        repo.add("Kotlin docs", "https://kotlinlang.org")
        repo.add("Other", "https://example.com/arabic")
        assertEquals(1, repo.observe(null, "kotlin").first().size)
        assertEquals(1, repo.observe(null, "arabic").first().size)
        assertEquals(0, repo.observe(null, "zzz").first().size)
    }

    @Test fun bookmarkEditPersists() = runBlocking {
        val repo = BookmarksRepository(db.bookmarkDao())
        val id = repo.add("Old", "https://old.com")
        val original = repo.observeAll().first().single()
        repo.update(id, "New", "https://new.com", null, original.createdAt)
        val updated = repo.observeAll().first().single()
        assertEquals("New", updated.title)
        assertEquals("https://new.com", updated.url)
    }

    private fun insertHistory(now: Long) = runBlocking {
        val dao = db.historyDao()
        dao.insert(HistoryEntity(url = "https://old.com", title = "old", visitedAt = now - 3L * 24 * 3600 * 1000))
        dao.insert(HistoryEntity(url = "https://today.com", title = "today", visitedAt = now - 2L * 3600 * 1000))
        dao.insert(HistoryEntity(url = "https://recent.com", title = "recent", visitedAt = now - 60 * 1000))
    }

    @Test fun clearingAnHourRemovesOnlyRecentVisits() = runBlocking {
        val now = 1_000_000_000_000L
        insertHistory(now)
        val repo = HistoryRepository(db.historyDao(), CoroutineScope(Dispatchers.Unconfined))
        repo.clear(ClearRange.LAST_HOUR, now)
        assertEquals(setOf("old", "today"), db.historyDao().getAll().map { it.title }.toSet())
    }

    @Test fun clearingADayKeepsOlderVisits() = runBlocking {
        val now = 1_000_000_000_000L
        insertHistory(now)
        val repo = HistoryRepository(db.historyDao(), CoroutineScope(Dispatchers.Unconfined))
        repo.clear(ClearRange.TODAY, now)
        assertEquals(listOf("old"), db.historyDao().getAll().map { it.title })
    }

    @Test fun clearingEverythingEmptiesHistory() = runBlocking {
        insertHistory(1_000_000_000_000L)
        val repo = HistoryRepository(db.historyDao(), CoroutineScope(Dispatchers.Unconfined))
        repo.clear(ClearRange.ALL)
        assertTrue(db.historyDao().getAll().isEmpty())
    }

    @Test fun historyRecordingStoresTitleAndUrlWithoutCredentials() = runBlocking {
        val repo = HistoryRepository(db.historyDao(), CoroutineScope(Dispatchers.Unconfined))
        repo.record("https://user:secret@example.com/page", "Page", 42L)
        withTimeout(5_000) { while (db.historyDao().getAll().isEmpty()) delay(20) }
        val entry = db.historyDao().getAll().single()
        assertEquals("https://example.com/page", entry.url)
        assertEquals("Page", entry.title)
    }

    @Test fun deletingASingleHistoryEntryWorks() = runBlocking {
        insertHistory(1_000_000_000_000L)
        val repo = HistoryRepository(db.historyDao(), CoroutineScope(Dispatchers.Unconfined))
        val victim = db.historyDao().getAll().first { it.title == "today" }
        repo.delete(victim.id)
        assertNull(db.historyDao().getAll().firstOrNull { it.title == "today" })
        assertEquals(2, db.historyDao().getAll().size)
    }

    @Test fun shortcutsCanBeAddedMovedAndRemoved() = runBlocking {
        val repo = ShortcutsRepository(db.shortcutDao())
        repo.seedDefaults()
        repo.seedDefaults() // idempotent
        assertEquals(4, db.shortcutDao().getAll().size)
        repo.add("Mine", "https://mine.com")
        val last = db.shortcutDao().getAll().last()
        repo.move(last.id, -1)
        assertEquals("Mine", db.shortcutDao().getAll()[3].title)
        repo.remove(last.id)
        assertEquals(4, db.shortcutDao().getAll().size)
        assertEquals((0..3).toList(), db.shortcutDao().getAll().map { it.position })
    }
}
