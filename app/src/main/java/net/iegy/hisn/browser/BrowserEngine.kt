package net.iegy.hisn.browser

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import net.iegy.hisn.tabs.PageError

/**
 * The seam between the app and the web rendering engine. Everything above this interface (tabs, UI, history,
 * private mode) is engine-agnostic; only [WebViewEngine] knows about android.webkit. A different engine can be
 * evaluated later by providing another implementation.
 */
interface BrowserEngine {
    fun createSession(callbacks: SessionCallbacks, desktopMode: Boolean): EngineSession

    /** Pauses/resumes engine-wide timers (call when the whole app goes to the background/foreground). */
    fun pauseAll()
    fun resumeAll()

    /** Clears cookies, site storage and the HTTP cache of this process's web data. */
    suspend fun clearWebsiteData()
}

interface EngineSession {
    val view: View
    fun load(url: String)
    fun reload()
    fun stop()
    fun goBack()
    fun goForward()
    fun setDesktopMode(enabled: Boolean)
    fun saveState(): Bundle?
    fun restoreState(state: Bundle): Boolean
    fun onPause()
    fun onResume()

    /** Find-in-page. Results are reported through [SessionCallbacks.onFindResult]. */
    fun findAll(query: String) {}
    fun findNext(forward: Boolean) {}
    fun clearFind() {}

    /** Switches the current page into / out of a clean reading layout. */
    fun toggleReader() {}

    /** Page text size in percent. */
    fun setTextZoom(percent: Int) {}

    fun setJavaScriptEnabled(enabled: Boolean) {}

    /** Opens the system print dialog (which also offers "Save as PDF"). */
    fun print(jobName: String) {}

    /** A small snapshot of the page for the tab switcher, or null when it cannot be taken. */
    fun captureThumbnail(targetWidth: Int): Bitmap? = null

    /**
     * Takes a full-resolution picture of the page: the visible part, or the whole page when [fullPage] is true
     * (the page is scrolled step by step, so it takes a moment). [onResult] is called on the main thread with
     * null when the picture could not be taken.
     */
    fun captureScreenshot(fullPage: Boolean, onResult: (PageScreenshot?) -> Unit) = onResult(null)

    /**
     * Runs [script] in the page and returns the string result on the main thread via [onResult].
     * Used for media discovery (video/source tags). Default is a no-op.
     */
    fun evaluateJavascript(script: String, onResult: (String?) -> Unit) = onResult(null)

    /**
     * Media URLs the engine observed while this page was loading (progressive mp4/webm, etc.).
     * Cleared on each main-frame navigation. Empty by default.
     */
    fun observedMediaUrls(): List<String> = emptyList()

    /** The view's Context delegates to the visible Activity (dialogs, pickers) and falls back to the app when none. */
    fun attachActivity(activity: Activity?)
    fun destroy()
}

/** [truncated] is true when a very long page was cut off at the bottom to keep memory use safe. */
class PageScreenshot(val bitmap: Bitmap, val truncated: Boolean)

class FileChooserRequest(
    val callback: ValueCallback<Array<Uri>>,
    val params: WebChromeClient.FileChooserParams,
)

/** What the user long-pressed. [linkUrl] and [imageUrl] are always http(s). */
data class LongPressTarget(val linkUrl: String?, val imageUrl: String?)

interface SessionCallbacks {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String, hadError: Boolean)
    fun onUrlChanged(url: String)
    fun onTitleChanged(title: String)
    fun onProgress(progress: Int)
    fun onNavigationState(canGoBack: Boolean, canGoForward: Boolean)
    fun onError(error: PageError)
    fun onBlockedCount(count: Int)

    /** [activeIndex] is zero-based; [total] is 0 when nothing matched. */
    fun onFindResult(activeIndex: Int, total: Int) {}

    /** True for the private window: no autofill, no suggestions leaving the device. */
    val isPrivateSession: Boolean get() = false

    fun currentTextZoom(): Int = 100

    /** False when the user switched JavaScript off for the site that [url] belongs to. */
    fun isJavaScriptAllowed(url: String): Boolean = true

    /** Returns the https:// address to load instead of this http:// one (HTTPS-only mode), or null to load as is. */
    fun upgradeUrl(url: String): String? = null

    /** Returns the session that should receive the new window's content, or null to refuse the popup. */
    fun onCreateWindow(): EngineSession?
    fun onCloseWindow()
    fun onExternalUrl(url: String)
    fun onDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long)
    fun onLongPress(target: LongPressTarget)
    fun onFileChooser(request: FileChooserRequest)
    fun onWebPermission(origin: String, resources: List<String>, grant: (List<String>) -> Unit, deny: () -> Unit)
    fun onShowCustomView(view: View, onHidden: () -> Unit)
    fun onHideCustomView()
    fun onRenderProcessGone(didCrash: Boolean)
}
