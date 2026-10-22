package net.iegy.hisn.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.iegy.hisn.browser.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private class RecordingMirror : BootMirror {
        var theme: ThemeMode? = null
        var language: AppLanguage? = null
        override fun onThemeChanged(theme: ThemeMode) { this.theme = theme }
        override fun onLanguageChanged(language: AppLanguage) { this.language = language }
    }

    private fun repo(file: File, scope: CoroutineScope, mirror: BootMirror? = null) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, mirror)

    @Test fun defaultsAreSensible() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val s = repo(File(tmp.root, "a.preferences_pb"), scope).settings.first()
        scope.cancel()
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(SearchEngine.GOOGLE, s.searchEngine)
        assertTrue(s.blockingEnabled)
        assertTrue(s.allowlist.isEmpty())
        assertEquals(StartupBehavior.RESTORE_TABS, s.startup)
    }

    @Test fun settingsSurviveRecreatingTheStore() = runBlocking {
        val file = File(tmp.root, "b.preferences_pb")

        val firstJob = SupervisorJob()
        val first = CoroutineScope(firstJob + Dispatchers.IO)
        val mirror = RecordingMirror()
        with(repo(file, first, mirror)) {
            setTheme(ThemeMode.DARK)
            setLanguage(AppLanguage.ARABIC)
            setSearchEngine(SearchEngine.DUCKDUCKGO)
            setBlockingEnabled(false)
            setDesktopByDefault(true)
            setStartup(StartupBehavior.RESTORE_TABS)
            setNewTabPage(NewTabPage.CUSTOM_URL)
            setCustomHomeUrl("https://example.org")
            addToAllowlist("WWW.Example.com")
        }
        // simulates the app being closed: wait until the first DataStore
        // has really released the file before opening it again
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        val second = CoroutineScope(secondJob + Dispatchers.IO)
        val s = repo(file, second).settings.first()
        secondJob.cancelAndJoin()

        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(AppLanguage.ARABIC, s.language)
        assertEquals(SearchEngine.DUCKDUCKGO, s.searchEngine)
        assertFalse(s.blockingEnabled)
        assertTrue(s.desktopByDefault)
        assertEquals(StartupBehavior.RESTORE_TABS, s.startup)
        assertEquals(NewTabPage.CUSTOM_URL, s.newTabPage)
        assertEquals("https://example.org", s.customHomeUrl)
        assertEquals(setOf("example.com"), s.allowlist)
        // The synchronous boot mirror received the two values that must be known before DataStore loads.
        assertEquals(ThemeMode.DARK, mirror.theme)
        assertEquals(AppLanguage.ARABIC, mirror.language)
    }

    @Test fun allowlistAddRemove() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val r = repo(File(tmp.root, "c.preferences_pb"), scope)
        r.addToAllowlist("a.com")
        r.addToAllowlist("b.com")
        r.removeFromAllowlist("www.a.com")
        val s = r.settings.first()
        scope.cancel()
        assertEquals(setOf("b.com"), s.allowlist)
    }
}
