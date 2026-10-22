package net.iegy.hisn.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.iegy.hisn.browser.HistoryRecorder
import net.iegy.hisn.browser.UrlResolver

/** How much history to wipe. [windowMillis] is null for "everything". */
enum class ClearRange(val windowMillis: Long?) {
    LAST_HOUR(60L * 60 * 1000),
    TODAY(24L * 60 * 60 * 1000),
    LAST_WEEK(7L * 24 * 60 * 60 * 1000),
    ALL(null),
}

class HistoryRepository(
    private val dao: HistoryDao,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HistoryRecorder {

    /** Fire-and-forget so page loads are never delayed by disk I/O. Only the URL and title are kept. */
    override fun record(url: String, title: String, now: Long) {
        if (url.length > MAX_URL_LENGTH) return
        // Credentials embedded in a URL (https://user:pass@host) are never written to disk.
        val cleanUrl = UrlResolver.stripUserInfo(url)
        scope.launch(dispatcher) {
            dao.insert(HistoryEntity(url = cleanUrl, title = title.take(300), visitedAt = now))
        }
    }

    fun observe(query: String): Flow<List<HistoryEntity>> = dao.observe(query.trim())

    fun observeRecent(limit: Int): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear(range: ClearRange, now: Long = System.currentTimeMillis()) {
        val window = range.windowMillis
        if (window == null) dao.deleteAll() else dao.deleteSince(now - window)
    }

    private companion object {
        const val MAX_URL_LENGTH = 4096
    }
}
