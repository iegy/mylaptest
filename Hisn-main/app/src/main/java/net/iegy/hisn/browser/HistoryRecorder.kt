package net.iegy.hisn.browser

/**
 * Sink for visited pages. Private browsing is given no recorder at all (null), so there is no code path
 * through which a private visit could reach regular history.
 */
interface HistoryRecorder {
    fun record(url: String, title: String, now: Long = System.currentTimeMillis())
}
