package net.iegy.hisn.tabs

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import net.iegy.hisn.browser.BrowserEngine
import net.iegy.hisn.browser.EngineSession
import net.iegy.hisn.browser.HistoryRecorder
import net.iegy.hisn.browser.SessionCallbacks
import net.iegy.hisn.download.DownloadController
import net.iegy.hisn.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TabManagerTest {
    private lateinit var context: Context
    private lateinit var engine: FakeEngine
    private val visits = mutableListOf<String>()
    private val recorder = object : HistoryRecorder {
        override fun record(url: String, title: String, now: Long) { visits += url }
    }

    private class FakeSession(context: Context, val callbacks: SessionCallbacks) : EngineSession {
        override val view: View = View(context)
        var destroyed = false
        val loaded = mutableListOf<String>()
        var state: Bundle? = Bundle()
        override fun load(url: String) { loaded += url }
        override fun reload() = Unit
        override fun stop() = Unit
        override fun goBack() = Unit
        override fun goForward() = Unit
        override fun setDesktopMode(enabled: Boolean) = Unit
        override fun saveState(): Bundle? = state
        override fun restoreState(state: Bundle) = true
        override fun onPause() = Unit
        override fun onResume() = Unit
        override fun attachActivity(activity: Activity?) = Unit
        override fun destroy() { destroyed = true }
    }

    private class FakeEngine(val context: Context) : BrowserEngine {
        val sessions = mutableListOf<FakeSession>()
        override fun createSession(callbacks: SessionCallbacks, desktopMode: Boolean): EngineSession =
            FakeSession(context, callbacks).also { sessions += it }
        override fun pauseAll() = Unit
        override fun resumeAll() = Unit
        override suspend fun clearWebsiteData() = Unit
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = FakeEngine(context)
        visits.clear()
    }

    private fun manager(isPrivate: Boolean, recorder: HistoryRecorder? = this.recorder): TabManager {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return TabManager(
            context = context,
            engine = engine,
            isPrivate = isPrivate,
            scope = scope,
            settings = MutableStateFlow(Settings()),
            history = recorder,
            tabsStore = null,
            downloads = DownloadController(context, null, scope),
        )
    }

    @Test fun `regular page visits are recorded in history`() {
        val tabs = manager(isPrivate = false)
        val id = tabs.newTab(url = "https://example.com")
        engine.sessions.single().callbacks.onPageFinished("https://example.com/", "Example", hadError = false)
        assertEquals(listOf("https://example.com/"), visits)
        assertEquals("https://example.com", tabs.model.value.tabs.single { it.id == id }.url)
    }

    @Test fun `private page visits never reach history even if a recorder is wired in`() {
        val tabs = manager(isPrivate = true)
        tabs.newTab(url = "https://secret.example")
        engine.sessions.single().callbacks.onPageFinished("https://secret.example/", "Secret", hadError = false)
        assertTrue(visits.isEmpty())
    }

    @Test fun `error pages are not recorded`() {
        val tabs = manager(isPrivate = false)
        tabs.newTab(url = "https://example.com")
        engine.sessions.single().callbacks.onPageFinished("https://example.com/", "Oops", hadError = true)
        assertTrue(visits.isEmpty())
    }

    @Test fun `creating selecting and closing tabs`() {
        val tabs = manager(isPrivate = false)
        val a = tabs.newTab()
        val b = tabs.newTab(url = "https://b.example")
        assertEquals(b, tabs.model.value.selectedId)
        tabs.select(a)
        assertEquals(a, tabs.model.value.selectedId)
        tabs.close(b)
        assertEquals(listOf(a), tabs.model.value.tabs.map { it.id })
        assertTrue("the session of the closed tab is destroyed", engine.sessions.single().destroyed)
    }

    @Test fun `a regular browser always keeps one tab but a private one may become empty`() {
        val regular = manager(isPrivate = false)
        regular.closeAll()
        assertEquals(1, regular.model.value.tabs.size)

        val private = manager(isPrivate = true)
        val id = private.newTab()
        private.close(id)
        assertTrue(private.model.value.tabs.isEmpty())
    }

    @Test fun `closed regular tabs can be reopened but private ones cannot`() {
        val regular = manager(isPrivate = false)
        val id = regular.newTab(url = "https://reopen.example")
        regular.close(id)
        assertTrue(regular.reopenClosed())
        assertEquals("https://reopen.example", regular.model.value.selected?.url)

        val private = manager(isPrivate = true)
        val pid = private.newTab(url = "https://private.example")
        private.close(pid)
        assertFalse(private.reopenClosed())
    }

    @Test fun `viewFor creates the engine view lazily and reuses it`() {
        val tabs = manager(isPrivate = false)
        val home = tabs.newTab()
        assertNull("start page has no engine view", tabs.viewFor(home))
        val page = tabs.newTab(url = "https://example.com")
        val first = tabs.viewFor(page)
        assertNotNull(first)
        assertTrue(first === tabs.viewFor(page))
        assertEquals("a page is loaded exactly once", listOf("https://example.com"), engine.sessions.single().loaded)
    }

    @Test fun `unsupported schemes never reach the engine`() {
        val tabs = manager(isPrivate = false)
        val id = tabs.newTab()
        tabs.navigate(id, "javascript:alert(1)")
        tabs.navigate(id, "file:///etc/passwd")
        assertTrue(engine.sessions.isEmpty())
        assertTrue(tabs.model.value.tabs.single().isHome)
    }

    @Test fun `low memory freezes background tabs and keeps the selected one`() {
        val tabs = manager(isPrivate = false)
        val a = tabs.newTab(url = "https://a.example")
        tabs.viewFor(a)
        val b = tabs.newTab(url = "https://b.example")
        tabs.viewFor(b)
        tabs.onLowMemory()
        val byUrl = engine.sessions
        assertTrue("background tab session destroyed", byUrl[0].destroyed)
        assertFalse("selected tab session kept", byUrl[1].destroyed)
        // Selecting the frozen tab again restores it instead of losing the tab.
        tabs.select(a)
        assertNotNull(tabs.viewFor(a))
        assertEquals(3, engine.sessions.size)
    }

    @Test fun `window creation from a page opens a child tab`() {
        val tabs = manager(isPrivate = false)
        val parent = tabs.newTab(url = "https://parent.example")
        val popup = engine.sessions.single().callbacks.onCreateWindow()
        assertNotNull(popup)
        assertEquals(2, tabs.model.value.tabs.size)
        assertEquals(parent, tabs.model.value.selected?.parentId)
    }
}
