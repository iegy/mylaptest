package net.iegy.hisn.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomPersistenceTest {
    private lateinit var context: Context
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB)
    }

    @After fun tearDown() {
        context.deleteDatabase(DB)
    }

    @Test fun `bookmarks persist after the database is closed and reopened`() = runBlocking {
        val first = HisnDatabase.create(context, DB)
        val repo = BookmarksRepository(first.bookmarkDao())
        val folder = repo.addFolder("News")
        repo.add("Example", "https://example.com", folder)
        repo.add("Root one", "https://root.example")
        first.close()

        val second = HisnDatabase.create(context, DB)
        val reopened = BookmarksRepository(second.bookmarkDao())
        assertEquals(listOf("Root one"), reopened.observe(null, "").first().map { it.title })
        assertEquals(listOf("Example"), reopened.observe(folder, "").first().map { it.title })
        assertEquals(listOf("News"), reopened.observeFolders().first().map { it.name })
        second.close()
    }

    @Test fun `bookmark toggle search edit and folder delete`() = runBlocking {
        val db = HisnDatabase.create(context, DB)
        val repo = BookmarksRepository(db.bookmarkDao())
        assertTrue(repo.toggle("https://a.com", "A"))
        assertFalse(repo.toggle("https://a.com", "A"))
        assertTrue(repo.observeAll().first().isEmpty())

        val folder = repo.addFolder("F")
        val id = repo.add("Alpha", "https://alpha.com", folder)
        assertEquals(1, repo.observe(null, "alph").first().size)
        val saved = repo.observeAll().first().single()
        repo.update(id, "Beta", "https://beta.com", null, saved.createdAt)
        assertEquals("Beta", repo.observeAll().first().single().title)

        repo.update(id, "Beta", "https://beta.com", folder, saved.createdAt)
        repo.deleteFolder(folder)
        val afterDelete = repo.observeAll().first().single()
        assertNull("bookmarks survive folder deletion by moving to root", afterDelete.folderId)
        db.close()
    }

    @Test fun `clearing history by range removes only the matching entries`() = runBlocking {
        val db = HisnDatabase.create(context, DB)
        val repo = HistoryRepository(db.historyDao(), scope)
        val now = 10L * 24 * 60 * 60 * 1000
        val dao = db.historyDao()
        dao.insert(HistoryEntity(url = "https://recent.com", title = "recent", visitedAt = now - 10 * 60 * 1000))
        dao.insert(HistoryEntity(url = "https://today.com", title = "today", visitedAt = now - 5 * 60 * 60 * 1000))
        dao.insert(HistoryEntity(url = "https://week.com", title = "week", visitedAt = now - 3L * 24 * 60 * 60 * 1000))
        dao.insert(HistoryEntity(url = "https://old.com", title = "old", visitedAt = now - 9L * 24 * 60 * 60 * 1000))

        repo.clear(ClearRange.LAST_HOUR, now)
        assertEquals(setOf("today", "week", "old"), dao.getAll().map { it.title }.toSet())
        repo.clear(ClearRange.TODAY, now)
        assertEquals(setOf("week", "old"), dao.getAll().map { it.title }.toSet())
        repo.clear(ClearRange.LAST_WEEK, now)
        assertEquals(setOf("old"), dao.getAll().map { it.title }.toSet())
        repo.clear(ClearRange.ALL, now)
        assertTrue(dao.getAll().isEmpty())
        db.close()
    }

    @Test fun `history search delete and credential stripping`() = runBlocking {
        val db = HisnDatabase.create(context, DB)
        val repo = HistoryRepository(db.historyDao(), scope)
        repo.record("https://user:secret@example.com/page", "Example page", 1_000L)
        withTimeout(3_000) { while (db.historyDao().getAll().isEmpty()) delay(10) }
        val entry = db.historyDao().getAll().single()
        assertEquals("https://example.com/page", entry.url)
        assertEquals(1, repo.observe("example").first().size)
        assertTrue(repo.observe("zzz").first().isEmpty())
        repo.delete(entry.id)
        assertTrue(db.historyDao().getAll().isEmpty())
        db.close()
    }

    @Test fun `shortcuts seed once reorder and remove`() = runBlocking {
        val db = HisnDatabase.create(context, DB)
        val repo = ShortcutsRepository(db.shortcutDao())
        repo.seedDefaults()
        repo.seedDefaults()
        var list = repo.observe().first()
        assertEquals(ShortcutsRepository.DEFAULT_SHORTCUTS.size, list.size)
        val first = list.first()
        repo.move(first.id, +1)
        list = repo.observe().first()
        assertEquals(first.id, list[1].id)
        repo.remove(first.id)
        list = repo.observe().first()
        assertEquals((0 until list.size).toList(), list.map { it.position })
        db.close()
    }

    private companion object {
        const val DB = "persistence-test.db"
    }
}
