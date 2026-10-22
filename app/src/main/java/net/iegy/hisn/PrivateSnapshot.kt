package net.iegy.hisn

import android.content.Intent
import net.iegy.hisn.browser.SearchEngine
import net.iegy.hisn.settings.AppLanguage
import net.iegy.hisn.settings.Settings
import net.iegy.hisn.settings.ThemeMode

/**
 * The subset of settings the private-browsing process needs, passed in the launch Intent.
 * The private process has no DataStore of its own, so nothing here is ever persisted there.
 */
object PrivateSnapshot {
    private const val EXTRA_ENGINE = "p_engine"
    private const val EXTRA_BLOCKING = "p_blocking"
    private const val EXTRA_ALLOWLIST = "p_allowlist"
    private const val EXTRA_DESKTOP = "p_desktop"
    private const val EXTRA_THEME = "p_theme"
    private const val EXTRA_LANGUAGE = "p_language"
    private const val EXTRA_TEXT_ZOOM = "p_text_zoom"
    private const val EXTRA_HTTPS_ONLY = "p_https_only"
    private const val EXTRA_JS_BLOCKED = "p_js_blocked"
    const val EXTRA_URL = "p_url"

    fun write(intent: Intent, settings: Settings, url: String? = null): Intent = intent.apply {
        putExtra(EXTRA_ENGINE, settings.searchEngine.id)
        putExtra(EXTRA_BLOCKING, settings.blockingEnabled)
        putExtra(EXTRA_ALLOWLIST, settings.allowlist.toTypedArray())
        putExtra(EXTRA_DESKTOP, settings.desktopByDefault)
        putExtra(EXTRA_THEME, settings.theme.name)
        putExtra(EXTRA_LANGUAGE, settings.language.name)
        putExtra(EXTRA_TEXT_ZOOM, settings.textZoom)
        putExtra(EXTRA_HTTPS_ONLY, settings.httpsOnly)
        putExtra(EXTRA_JS_BLOCKED, settings.jsBlockedHosts.toTypedArray())
        url?.let { putExtra(EXTRA_URL, it) }
    }

    fun read(intent: Intent, base: Settings): Settings = base.copy(
        searchEngine = SearchEngine.fromId(intent.getStringExtra(EXTRA_ENGINE)),
        blockingEnabled = intent.getBooleanExtra(EXTRA_BLOCKING, base.blockingEnabled),
        allowlist = intent.getStringArrayExtra(EXTRA_ALLOWLIST)?.toSet() ?: base.allowlist,
        desktopByDefault = intent.getBooleanExtra(EXTRA_DESKTOP, base.desktopByDefault),
        theme = ThemeMode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_THEME) } ?: base.theme,
        language = AppLanguage.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_LANGUAGE) } ?: base.language,
        textZoom = intent.getIntExtra(EXTRA_TEXT_ZOOM, base.textZoom),
        httpsOnly = intent.getBooleanExtra(EXTRA_HTTPS_ONLY, base.httpsOnly),
        jsBlockedHosts = intent.getStringArrayExtra(EXTRA_JS_BLOCKED)?.toSet() ?: base.jsBlockedHosts,
    )
}
