package net.iegy.hisn.rafiqi

/**
 * Virtual origin used by [androidx.webkit.WebViewAssetLoader] to serve the
 * offline Rafiqi PWA from `assets/rafiqi/` without exposing the file:// scheme.
 *
 * The host must match what WebViewAssetLoader generates (default domain is
 * appassets.androidplatform.net; we use a custom domain for clarity and
 * allowlisting).
 */
object RafiqiUrls {
    /** Origin that the AssetLoader serves. Always https. */
    const val ORIGIN = "https://rafiqi.hisn.local"

    /** Entry point of the local PWA. */
    const val HOME = "$ORIGIN/"

    /** Online fallback when local assets are not populated. */
    const val ONLINE = "https://rafiqi.iegy.net/"

    /** Host part for matching (no scheme). */
    const val HOST = "rafiqi.hisn.local"

    fun isLocalRafiqi(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith(ORIGIN) || lower.contains("://$HOST")
    }

    fun isRafiqiHost(url: String): Boolean {
        val host = net.iegy.hisn.browser.UrlResolver.hostOf(url) ?: return false
        return host == HOST || host == "rafiqi.iegy.net" || host.endsWith(".rafiqi.iegy.net")
    }
}
