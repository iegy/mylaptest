package net.iegy.hisn.rafiqi

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import java.io.IOException

/**
 * Sets up local asset serving + the secure JavascriptInterface for Rafiqi.
 */
object RafiqiWebSupport {

    private const val ASSET_ROOT = "rafiqi"

    class RafiqiAssetsHandler(private val context: Context) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val relative = when {
                path.isEmpty() || path == "/" -> "index.html"
                else -> path.removePrefix("/")
            }
            val assetPath = "$ASSET_ROOT/$relative"
            return try {
                val stream = context.assets.open(assetPath)
                WebResourceResponse(guessMime(relative), "UTF-8", stream)
            } catch (_: IOException) {
                null
            }
        }

        private fun guessMime(path: String): String {
            val lower = path.lowercase()
            return when {
                lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
                lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
                lower.endsWith(".css") -> "text/css"
                lower.endsWith(".json") -> "application/json"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".svg") -> "image/svg+xml"
                lower.endsWith(".woff2") -> "font/woff2"
                lower.endsWith(".woff") -> "font/woff"
                lower.endsWith(".ttf") -> "font/ttf"
                lower.endsWith(".mp3") -> "audio/mpeg"
                lower.endsWith(".webmanifest") || lower.endsWith(".manifest") -> "application/manifest+json"
                else -> "application/octet-stream"
            }
        }
    }

    fun buildLoader(context: Context): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .setDomain(RafiqiUrls.HOST)
            .addPathHandler("/", RafiqiAssetsHandler(context))
            .build()
    }

    @SuppressLint("JavascriptInterface")
    fun attachBridge(webView: WebView, context: Context, scheduler: PrayerScheduler) {
        try {
            webView.removeJavascriptInterface("HisnRafiqi")
        } catch (_: Exception) {
        }
        webView.addJavascriptInterface(
            RafiqiBridge(context.applicationContext, scheduler),
            "HisnRafiqi",
        )
    }

    fun detachBridge(webView: WebView) {
        try {
            webView.removeJavascriptInterface("HisnRafiqi")
        } catch (_: Exception) {
        }
    }

    fun injectHelperScript(): String = """
        (function(){
          if (window.__hisnRafiqiReady) return;
          window.__hisnRafiqiReady = true;
          if (!window.HisnRafiqi) return;
          window.HisnRafiqi.schedule = function(prayers, city) {
            try {
              return window.HisnRafiqi.schedulePrayers(JSON.stringify({
                city: city || '',
                prayers: prayers || []
              }));
            } catch(e) { return 'error:' + e; }
          };
          console.log('[Hisn] Rafiqi native bridge ready');
        })();
    """.trimIndent()
}
