package net.iegy.hisn.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintManager
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Uri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import net.iegy.hisn.blocking.BlockingEngine
import net.iegy.hisn.tabs.PageError
import net.iegy.hisn.tabs.PageErrorKind

class WebViewEngine(
    private val appContext: Context,
    private val blocking: BlockingEngine,
) : BrowserEngine {

    // Weak so a destroyed session can be collected; only used to reach the engine-wide pause/resume calls.
    private val live = java.util.WeakHashMap<WebViewSession, Boolean>()

    override fun createSession(callbacks: SessionCallbacks, desktopMode: Boolean): EngineSession =
        WebViewSession(appContext, blocking, callbacks, desktopMode).also { live[it] = true }

    private fun anyLiveView(): WebView? = live.keys.firstOrNull { !it.isDestroyed }?.webView

    // pauseTimers/resumeTimers are instance methods, but they act on every WebView in the process.
    override fun pauseAll() {
        anyLiveView()?.pauseTimers()
    }

    override fun resumeAll() {
        anyLiveView()?.resumeTimers()
    }

    override suspend fun clearWebsiteData() {
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        // clearCache is an instance method that affects the shared HTTP cache, so a short-lived view is enough.
        WebView(MutableContextWrapper(appContext)).apply {
            clearCache(true)
            clearFormData()
            destroy()
        }
    }
}

/** One WebView = one tab's engine session. */
@SuppressLint("SetJavaScriptEnabled")
internal class WebViewSession(
    appContext: Context,
    private val blocking: BlockingEngine,
    private val callbacks: SessionCallbacks,
    desktopMode: Boolean,
) : EngineSession {

    private val contextWrapper = MutableContextWrapper(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Read on WebView's IO threads in shouldInterceptRequest, hence volatile.
    @Volatile private var pageHost: String? = null
    @Volatile private var hadError = false
    private val blockedCount = AtomicInteger(0)
    private val blockedFlushPending = AtomicBoolean(false)
    private val mobileUserAgent: String = WebSettings.getDefaultUserAgent(appContext)
    private var destroyed = false
    val isDestroyed: Boolean get() = destroyed

    /** Progressive media URLs seen while the current page loaded (cleared on main-frame navigation). */
    private val observedMedia = java.util.concurrent.ConcurrentLinkedDeque<String>()
    private val observedMediaLock = Any()

    /** Serves offline Rafiqi from assets/rafiqi under https://rafiqi.hisn.local/ */
    private val rafiqiAssetLoader = net.iegy.hisn.rafiqi.RafiqiWebSupport.buildLoader(appContext)
    private val prayerScheduler = net.iegy.hisn.rafiqi.PrayerScheduler(appContext.applicationContext)

    val webView: WebView = WebView(contextWrapper)
    override val view: View get() = webView

    init {
        configure(desktopMode)
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            callbacks.onDownload(url, userAgent, contentDisposition, mimeType, contentLength)
        }
        webView.setOnLongClickListener { handleLongPress() }
        webView.setFindListener { active, total, _ -> callbacks.onFindResult(active, total) }
        webView.settings.textZoom = callbacks.currentTextZoom()
        // Private pages never reach an autofill service (it could remember what was typed); regular pages may use one.
        webView.importantForAutofill =
            if (callbacks.isPrivateSession) View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            else View.IMPORTANT_FOR_AUTOFILL_YES
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
    }

    private fun configure(desktopMode: Boolean) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Local files and content providers are never reachable from web content.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Popups are only honoured for real user gestures (see Chrome.onCreateWindow).
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            // Location is not requested by this app, so geolocation is not offered to pages.
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = if (desktopMode) UserAgents.desktop(mobileUserAgent) else null
            
            // تفعيل الحماية
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, true)
            }
            // Darken bright sites only when the system is in night mode. The private window uses an always-dark
            // Activity theme, so without this check WebView would darken every page there even in daytime.
            val night = (webView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, night)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    this,
                    if (night) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
                )
            }
        }
        webView.isVerticalScrollBarEnabled = true
        webView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun load(url: String) {
        if (destroyed) return
        webView.settings.javaScriptEnabled = callbacks.isJavaScriptAllowed(url)
        webView.loadUrl(url)
    }

    override fun reload() {
        if (!destroyed) webView.reload()
    }

    override fun stop() {
        if (!destroyed) webView.stopLoading()
    }

    override fun goBack() {
        if (!destroyed && webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (!destroyed && webView.canGoForward()) webView.goForward()
    }

    override fun setDesktopMode(enabled: Boolean) {
        if (destroyed) return
        webView.settings.userAgentString = if (enabled) UserAgents.desktop(mobileUserAgent) else null
        webView.reload()
    }

    override fun saveState(): Bundle? {
        if (destroyed) return null
        val bundle = Bundle()
        return if (webView.saveState(bundle) != null) bundle else null
    }

    override fun restoreState(state: Bundle): Boolean =
        !destroyed && webView.restoreState(state) != null

    override fun onPause() {
        if (!destroyed) webView.onPause()
    }

    override fun onResume() {
        if (!destroyed) webView.onResume()
    }

    override fun attachActivity(activity: Activity?) {
        contextWrapper.baseContext = activity ?: contextWrapper.baseContext.applicationContext
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.setDownloadListener(null)
        webView.setOnLongClickListener(null)
        webView.destroy()
    }

    override fun setTextZoom(percent: Int) {
        if (!destroyed) webView.settings.textZoom = percent
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        if (!destroyed) webView.settings.javaScriptEnabled = enabled
    }

    override fun evaluateJavascript(script: String, onResult: (String?) -> Unit) {
        if (destroyed) {
            onResult(null)
            return
        }
        webView.evaluateJavascript(script) { result -> onResult(result) }
    }

    override fun observedMediaUrls(): List<String> {
        synchronized(observedMediaLock) {
            return observedMedia.toList()
        }
    }

    private fun noteMediaUrl(url: String?) {
        if (url.isNullOrBlank()) return
        if (!MediaDownload.isDownloadableMediaUrl(url) && !url.lowercase().let {
                it.contains("mime=video") || it.contains(".mp4") || it.contains(".webm") || it.contains(".m4a")
            }
        ) return
        synchronized(observedMediaLock) {
            if (observedMedia.size >= 40) observedMedia.pollFirst()
            if (!observedMedia.contains(url)) observedMedia.addLast(url)
        }
    }

    private fun clearObservedMedia() {
        synchronized(observedMediaLock) { observedMedia.clear() }
    }

    override fun print(jobName: String) {
        if (destroyed) return
        val manager = webView.context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val name = jobName.ifBlank { "Hisn" }
        manager.print(name, webView.createPrintDocumentAdapter(name), null)
    }

    override fun captureThumbnail(targetWidth: Int): Bitmap? {
        if (destroyed || webView.width <= 0 || webView.height <= 0) return null
        return try {
            val scale = targetWidth.toFloat() / webView.width
            val height = (webView.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    override fun captureScreenshot(fullPage: Boolean, onResult: (PageScreenshot?) -> Unit) {
        if (destroyed || webView.width <= 0 || webView.height <= 0) {
            onResult(null)
            return
        }
        if (fullPage) captureFullPage(onResult) else grabViewport { bitmap -> onResult(bitmap?.let { PageScreenshot(it, false) }) }
    }

    /** The Activity window this WebView is shown in, or null when the view is not attached to one. */
    private fun hostWindow(): Window? {
        var c: Context? = webView.context
        while (c is ContextWrapper) {
            if (c is Activity) return c.window
            c = c.baseContext
        }
        return null
    }

    /**
     * What is on screen right now, copied pixel for pixel from the window with PixelCopy. Unlike View.draw(), this
     * captures exactly what the GPU shows (sharp text, correct scroll position, no blank areas). When PixelCopy cannot
     * be used it falls back to drawing the view. [onResult] runs on the main thread.
     */
    private fun grabViewport(onResult: (Bitmap?) -> Unit) {
        val window = hostWindow()
        if (window == null || !webView.isShown) {
            onResult(drawViewport())
            return
        }
        val width = webView.width
        val height = webView.height
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            onResult(null)
            return
        }
        val location = IntArray(2)
        webView.getLocationInWindow(location)
        val source = Rect(location[0], location[1], location[0] + width, location[1] + height)
        try {
            PixelCopy.request(window, source, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    bitmap.recycle()
                    onResult(drawViewport())
                }
            }, mainHandler)
        } catch (e: IllegalArgumentException) {
            bitmap.recycle()
            onResult(drawViewport())
        }
    }

    /** Fallback: draws the view into a bitmap (software path; can be softer or blank for scrolled pages). */
    private fun drawViewport(): Bitmap? = try {
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // A white base avoids transparent (black) areas in the PNG when the page sets no background colour.
        canvas.drawColor(android.graphics.Color.WHITE)
        webView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /**
     * Scrolls from the top to the bottom one screen at a time, copies each screen from the window into one tall
     * bitmap, then puts the page back where the user was. Fixed and sticky elements (headers, cookie bars) are hidden
     * after the first screen so they are not repeated on every slice.
     */
    private fun captureFullPage(onResult: (PageScreenshot?) -> Unit) {
        val width = webView.width
        val viewportHeight = webView.height
        // contentHeight is in CSS pixels; the scale turns it into the device pixels the bitmap uses.
        @Suppress("DEPRECATION")
        val contentPx = (webView.contentHeight * webView.scale).toInt()
        // Very long pages are cut at a pixel budget so the bitmap cannot exhaust memory.
        val maxHeight = MAX_SCREENSHOT_PIXELS / width
        val targetHeight = minOf(maxOf(contentPx, viewportHeight), maxHeight)
        val truncated = contentPx > maxHeight
        if (targetHeight <= viewportHeight) {
            grabViewport { bitmap -> onResult(bitmap?.let { PageScreenshot(it, false) }) }
            return
        }
        val output = try {
            Bitmap.createBitmap(width, targetHeight, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            onResult(null)
            return
        }
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        val startX = webView.scrollX
        val startY = webView.scrollY
        var covered = 0 // bottom edge of what has been drawn so far
        var fixedHidden = false

        fun finish(success: Boolean) {
            if (!destroyed) {
                webView.evaluateJavascript(SHOT_SHOW_FIXED_JS, null)
                webView.scrollTo(startX, startY)
            }
            if (!success) {
                output.recycle()
                onResult(null)
                return
            }
            // The page can turn out shorter than its estimated height; cut the empty white part off.
            val finalHeight = minOf(covered, targetHeight)
            val result = if (finalHeight < targetHeight) {
                Bitmap.createBitmap(output, 0, 0, width, finalHeight).also { output.recycle() }
            } else {
                output
            }
            onResult(PageScreenshot(result, truncated))
        }

        fun captureSlice(y: Int) {
            if (destroyed) {
                finish(false)
                return
            }
            webView.scrollTo(startX, y)
            // Gives WebView time to scroll and paint the newly revealed part before the screen is copied.
            mainHandler.postDelayed({
                if (destroyed) {
                    finish(false)
                    return@postDelayed
                }
                // scrollTo stops at the end of the page, so the real position can be above the requested one.
                val actualY = webView.scrollY
                grabViewport { slice ->
                    if (slice == null || destroyed) {
                        slice?.recycle()
                        finish(false)
                        return@grabViewport
                    }
                    canvas.drawBitmap(slice, 0f, actualY.toFloat(), null)
                    slice.recycle()
                    val newCovered = actualY + viewportHeight
                    val progressed = newCovered > covered
                    covered = maxOf(covered, newCovered)
                    if (covered >= targetHeight || !progressed) {
                        finish(true)
                    } else {
                        if (!fixedHidden) {
                            fixedHidden = true
                            webView.evaluateJavascript(SHOT_HIDE_FIXED_JS, null)
                        }
                        captureSlice(covered)
                    }
                }
            }, SHOT_SETTLE_MS)
        }

        captureSlice(0)
    }

    override fun findAll(query: String) {
        if (destroyed) return
        if (query.isBlank()) {
            webView.clearMatches()
            callbacks.onFindResult(0, 0)
        } else {
            webView.findAllAsync(query)
        }
    }

    override fun findNext(forward: Boolean) {
        if (!destroyed) webView.findNext(forward)
    }

    override fun clearFind() {
        if (!destroyed) webView.clearMatches()
    }

    override fun toggleReader() {
        if (destroyed) return
        val readerScript = """
            (function() {
                var KEY = '__hisnReader';
                // الخروج من وضع القراءة: نُرجع العقد الأصلية كما هي (مع مستمعي الأحداث) دون إعادة تحميل الصفحة.
                if (window[KEY]) {
                    var saved = window[KEY];
                    document.body.innerHTML = '';
                    document.body.appendChild(saved.nodes);
                    document.body.style.cssText = saved.bodyStyle;
                    if (saved.dir === null) document.documentElement.removeAttribute('dir');
                    else document.documentElement.setAttribute('dir', saved.dir);
                    window[KEY] = null;
                    window.scrollTo(0, 0);
                    return;
                }

                // اختيار أفضل حاوية للمقال: article ثم main ثم الأب الذي يحوي أكبر نص فقرات.
                var article = document.querySelector('article') || document.querySelector('main') ||
                    document.querySelector('[role="main"]');
                if (!article) {
                    var scores = new Map();
                    document.querySelectorAll('p').forEach(function(p) {
                        var len = (p.textContent || '').trim().length;
                        if (len >= 40 && p.parentElement) {
                            scores.set(p.parentElement, (scores.get(p.parentElement) || 0) + len);
                        }
                    });
                    var best = 0;
                    scores.forEach(function(score, el) { if (score > best) { best = score; article = el; } });
                }
                if (!article) article = document.body;

                var content = article.cloneNode(true);

                // حذف العناصر غير المقروءة. الكلمات تُفحص ككلمات كاملة حتى لا تُحذف lead- أو head- أو thread- أو download-.
                content.querySelectorAll('nav, aside, footer, script, style, noscript, iframe, form, button, ins').forEach(function(e) { e.remove(); });
                content.querySelectorAll('header').forEach(function(h) {
                    if (!h.querySelector('h1, h2, h3')) h.remove();
                });
                var adWords = ['ad', 'ads', 'advert', 'advertisement', 'adsbygoogle', 'sponsored', 'comments', 'comment'];
                content.querySelectorAll('[class], [id]').forEach(function(e) {
                    var raw = (typeof e.className === 'string' ? e.className : '') + ' ' + (e.id || '');
                    var tokens = raw.toLowerCase().split(/[^a-z0-9]+/);
                    for (var i = 0; i < tokens.length; i++) {
                        if (adWords.indexOf(tokens[i]) !== -1) { e.remove(); return; }
                    }
                });

                // اتجاه النص: RTL إذا كان أغلب الحروف عربية/عبرية/فارسية.
                var sample = (content.textContent || '').replace(/\s+/g, '').substring(0, 400);
                var rtlChars = (sample.match(/[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]/g) || []).length;
                var rtl = sample.length > 0 && rtlChars / sample.length > 0.4;

                // نحفظ العقد الأصلية في fragment (تبقى حيّة بمستمعيها) ونبني صفحة القراءة.
                var nodes = document.createDocumentFragment();
                while (document.body.firstChild) nodes.appendChild(document.body.firstChild);
                window[KEY] = {
                    nodes: nodes,
                    bodyStyle: document.body.style.cssText,
                    dir: document.documentElement.getAttribute('dir')
                };
                document.documentElement.setAttribute('dir', rtl ? 'rtl' : 'ltr');
                document.body.appendChild(content);

                // التأقلم التلقائي مع الوضع المظلم أو الفاتح
                var isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                var st = document.body.style;
                st.backgroundColor = isDark ? '#202124' : '#F8F9FA';
                st.color = isDark ? '#E8EAED' : '#202124';
                st.padding = '24px';
                st.fontSize = '22px';
                st.lineHeight = '1.8';
                st.fontFamily = 'sans-serif';
                st.maxWidth = '800px';
                st.margin = '0 auto';
                st.textAlign = rtl ? 'right' : 'left';

                content.querySelectorAll('img').forEach(function(img) {
                    img.style.maxWidth = '100%';
                    img.style.height = 'auto';
                    img.style.borderRadius = '12px';
                    img.style.margin = '16px 0';
                });
                window.scrollTo(0, 0);
            })();
        """.trimIndent()
        webView.evaluateJavascript(readerScript, null)
    }

    private fun publishNavigationState() {
        callbacks.onNavigationState(webView.canGoBack(), webView.canGoForward())
    }

    private fun scheduleBlockedFlush() {
        if (blockedFlushPending.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                blockedFlushPending.set(false)
                if (!destroyed) callbacks.onBlockedCount(blockedCount.get())
            }, 300)
        }
    }

    private fun handleLongPress(): Boolean {
        val result = webView.hitTestResult
        val extra = result.extra?.takeIf { UrlResolver.isWebUrl(it) } ?: return false
        return when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                callbacks.onLongPress(LongPressTarget(linkUrl = extra, imageUrl = null))
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                callbacks.onLongPress(LongPressTarget(linkUrl = null, imageUrl = extra))
                true
            }
            else -> false
        }
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when {
                UrlResolver.isWebUrl(url) -> {
                    if (!request.isForMainFrame) {
                        false
                    } else {
                        val upgraded = callbacks.upgradeUrl(url)
                        if (upgraded != null) {
                            view.loadUrl(upgraded)
                            true
                        } else {
                            webView.settings.javaScriptEnabled = callbacks.isJavaScriptAllowed(url)
                            false
                        }
                    }
                }
                url == "about:blank" -> false
                UrlResolver.isExternalScheme(url) -> {
                    callbacks.onExternalUrl(url)
                    true
                }
                // javascript:, file:, content:, data:, etc. are never navigated to from a page.
                else -> true
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Offline Rafiqi PWA from assets (virtual https://rafiqi.hisn.local/).
            rafiqiAssetLoader.shouldInterceptRequest(request.url)?.let { return it }

            if (!request.isForMainFrame) {
                // Remember progressive media URLs so "Download media" can offer them later.
                val u = request.url?.toString()
                if (u != null) noteMediaUrl(u)
            }
            if (request.isForMainFrame) return null
            val host = request.url.host
            // Never block Rafiqi (local or online).
            if (net.iegy.hisn.rafiqi.RafiqiUrls.isRafiqiHost(request.url.toString())) return null
            if (!blocking.shouldBlock(request.url.toString(), host, pageHost)) return null
            blockedCount.incrementAndGet()
            scheduleBlockedFlush()
            return WebResourceResponse(
                "text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)),
            )
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            hadError = false
            clearObservedMedia()
            val newHost = UrlResolver.hostOf(url)
            if (newHost != pageHost) {
                blockedCount.set(0)
                callbacks.onBlockedCount(0)
            }
            pageHost = newHost

            // Native bridge only for the local Rafiqi origin.
            if (net.iegy.hisn.rafiqi.RafiqiUrls.isLocalRafiqi(url)) {
                net.iegy.hisn.rafiqi.RafiqiWebSupport.attachBridge(
                    webView,
                    contextWrapper.baseContext,
                    prayerScheduler,
                )
            } else {
                net.iegy.hisn.rafiqi.RafiqiWebSupport.detachBridge(webView)
            }

            callbacks.onPageStarted(url)
            publishNavigationState()
        }

        override fun onPageFinished(view: WebView, url: String) {
            // Cosmetic filtering uses only parsed, bounded selectors; no remote script is executed.
            blocking.cosmeticScript(UrlResolver.hostOf(url)).takeIf { it.isNotBlank() }?.let { script ->
                view.evaluateJavascript(script, null)
            }
            if (net.iegy.hisn.rafiqi.RafiqiUrls.isLocalRafiqi(url)) {
                view.evaluateJavascript(net.iegy.hisn.rafiqi.RafiqiWebSupport.injectHelperScript(), null)
            }
            
            // مستعرض الميديا: تحويل الصفحة لمعرض ميديا أسود إذا كان الرابط مباشراً
            if (isDirectMediaUrl(url)) {
                // يُطبَّق فقط إذا كانت الصفحة فعلاً مستند وسائط مباشر (عنصر img/video وحيد)، لا صفحة HTML عادية.
                val mediaScript = """
                    (function() {
                        var b = document.body;
                        if (!b || b.children.length !== 1) return;
                        var media = b.children[0];
                        if (!/^(IMG|VIDEO)$/.test(media.tagName)) return;
                        b.style.backgroundColor = '#000000';
                        b.style.margin = '0';
                        b.style.display = 'flex';
                        b.style.justifyContent = 'center';
                        b.style.alignItems = 'center';
                        b.style.height = '100vh';
                        media.style.maxWidth = '100%';
                        media.style.maxHeight = '100%';
                        media.style.objectFit = 'contain';
                    })();
                """.trimIndent()
                view.evaluateJavascript(mediaScript, null)
            }

            callbacks.onPageFinished(url, view.title.orEmpty(), hadError)
            publishNavigationState()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (UrlResolver.isWebUrl(url)) {
                pageHost = UrlResolver.hostOf(url)
                callbacks.onUrlChanged(url)
            }
            publishNavigationState()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val description = error.description?.toString().orEmpty()
            // ERR_ABORTED fires when a navigation turns into a download or is superseded; not a failure.
            if (description.contains("ERR_ABORTED")) return
            hadError = true
            val kind = when (error.errorCode) {
                ERROR_HOST_LOOKUP -> PageErrorKind.DNS
                ERROR_TIMEOUT -> PageErrorKind.TIMEOUT
                ERROR_CONNECT, ERROR_IO -> PageErrorKind.CONNECTION
                ERROR_FAILED_SSL_HANDSHAKE -> PageErrorKind.SSL
                else -> PageErrorKind.GENERIC
            }
            callbacks.onError(PageError(kind, request.url.toString(), error.errorCode, description))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Certificate validation is never bypassed: the load is cancelled and the user sees an error page.
            handler.cancel()
            hadError = true
            callbacks.onError(PageError(PageErrorKind.SSL, error.url.orEmpty(), error.primaryError))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            callbacks.onRenderProcessGone(detail.didCrash())
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            callbacks.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            callbacks.onTitleChanged(title.orEmpty())
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Scripted popups without a user gesture are refused outright.
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val target = callbacks.onCreateWindow() as? WebViewSession ?: return false
            transport.webView = target.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            callbacks.onCloseWindow()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            callbacks.onFileChooser(FileChooserRequest(filePathCallback, fileChooserParams))
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val supported = request.resources.filter {
                it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
            }
            if (supported.isEmpty()) {
                request.deny()
                return
            }
            callbacks.onWebPermission(
                origin = request.origin.toString(),
                resources = supported,
                grant = { granted -> request.grant(granted.toTypedArray()) },
                deny = { request.deny() },
            )
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            callbacks.onShowCustomView(view) { callback.onCustomViewHidden() }
        }

        override fun onHideCustomView() {
            callbacks.onHideCustomView()
        }

        override fun getDefaultVideoPoster(): Bitmap? = null
    }
}

/** About 16 million pixels (~64 MB as ARGB_8888): the largest screenshot bitmap we are willing to allocate. */
private const val MAX_SCREENSHOT_PIXELS = 16_000_000

private const val SHOT_SETTLE_MS = 500L

/** Hides position:fixed / position:sticky elements (keeping their space) while a full-page screenshot is stitched. */
private const val SHOT_HIDE_FIXED_JS =
    "(function(){var h=[];document.querySelectorAll('*').forEach(function(e){var p=getComputedStyle(e).position;" +
        "if(p==='fixed'||p==='sticky'){h.push([e,e.style.visibility]);e.style.visibility='hidden';}});" +
        "window.__hisnShotHidden=h;})();"

private const val SHOT_SHOW_FIXED_JS =
    "(function(){var h=window.__hisnShotHidden||[];h.forEach(function(x){x[0].style.visibility=x[1];});" +
        "window.__hisnShotHidden=null;})();"
