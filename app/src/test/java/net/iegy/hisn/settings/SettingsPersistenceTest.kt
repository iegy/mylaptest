package net.iegy.hisn.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class SettingsPersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    private class RecordingMirror : BootMirror {
        var theme: ThemeMode? = null
        var language: AppLanguage? = null
        override fun onThemeChanged(theme: ThemeMode) { this.theme = theme }
        override fun onLanguageChanged(language: AppLanguage) { this.language = language }
    }

    private fun repoOn(file: File, scope: CoroutineScope, mirror: BootMirror? = null) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }, mirror)

    @Test fun `defaults are sensible`() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val s = repoOn(File(tmp.root, "d.preferences_pb"), scope).settings.first()
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(SearchEngine.GOOGLE, s.searchEngine)
        assertTrue(s.blockingEnabled)
        assertTrue(s.allowlist.isEmpty())
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test fun `settings survive recreating the data store`() = runBlocking {
        val file = File(tmp.root, "s.preferences_pb")
        val mirror = RecordingMirror()

        val scope1 = CoroutineScope(Job() + Dispatchers.IO)
        val repo1 = repoOn(file, scope1, mirror)
        repo1.setTheme(ThemeMode.DARK)
        repo1.setLanguage(AppLanguage.ARABIC)
        repo1.setSearchEngine(SearchEngine.DUCKDUCKGO)
        repo1.setBlockingEnabled(false)
        repo1.setStartup(StartupBehavior.RESTORE_TABS)
        repo1.setDesktopByDefault(true)
        repo1.addToAllowlist("WWW.Example.com")
        repo1.addToAllowlist("other.org")
        repo1.removeFromAllowlist("other.org")
        scope1.coroutineContext[Job]!!.cancelAndJoin()

        val scope2 = CoroutineScope(Job() + Dispatchers.IO)
        val s = repoOn(file, scope2).settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(AppLanguage.ARABIC, s.language)
        assertEquals(SearchEngine.DUCKDUCKGO, s.searchEngine)
        assertFalse(s.blockingEnabled)
        assertEquals(StartupBehavior.RESTORE_TABS, s.startup)
        assertTrue(s.desktopByDefault)
        assertEquals(setOf("example.com"), s.allowlist)
        scope2.coroutineContext[Job]!!.cancelAndJoin()

        // Theme and language are also mirrored synchronously for the next cold start.
        assertEquals(ThemeMode.DARK, mirror.theme)
        assertEquals(AppLanguage.ARABIC, mirror.language)
    }
}
