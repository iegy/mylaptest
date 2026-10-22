package net.iegy.hisn.browser

import java.net.URLEncoder
import java.util.Locale

/** Search engines selectable in settings. Labels are brand names and are intentionally not translated. */
enum class SearchEngine(val id: String, val label: String, private val template: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=");

    /** Builds a search URL. The query is percent-encoded as UTF-8, so Arabic and other Unicode text survives. */
    fun searchUrl(query: String): String = template + URLEncoder.encode(query, "UTF-8")

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

enum class RejectReason { EMPTY, UNSUPPORTED_SCHEME, INVALID }

/** Outcome of interpreting whatever the user typed into the address bar. */
sealed interface ResolvedInput {
    /** Load this http(s) URL in the tab. */
    data class Navigate(val url: String) : ResolvedInput

    /** The text was an ordinary query; [url] is the search-engine URL for it. */
    data class Search(val url: String, val query: String) : ResolvedInput

    /** mailto:, tel: and similar. Never loaded in the WebView; handed to Android after user confirmation. */
    data class External(val url: String) : ResolvedInput

    data class Rejected(val reason: RejectReason) : ResolvedInput
}

/**
 * Decides whether address-bar text is a URL, a search query, an external-app link or something to refuse.
 *
 * Deliberate rules:
 *  - Only http and https are ever navigated to. `javascript:`, `file:`, `content:`, `data:` and friends are rejected.
 *  - Text is never interpreted as a local file path: anything path-like becomes a search query.
 *  - Host:port input such as `localhost:8080` is recognised before scheme parsing so it is not mistaken for a scheme.
 */
object UrlResolver {
    private val SCHEME_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")
    private val HOST_PORT_REGEX = Regex("^[^\\s:/?#]+:\\d{1,5}([/?#].*)?$")
    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    // Unicode-aware so internationalised domain names (including Arabic labels) are accepted.
    private val DOMAIN_REGEX =
        Regex("^([\\p{L}\\p{N}]([\\p{L}\\p{N}\\-]*[\\p{L}\\p{N}])?\\.)+\\p{L}[\\p{L}\\p{N}\\-]{1,62}$")

    private val WEB_SCHEMES = setOf("http", "https")
    private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "geo", "market", "intent")
    private val BLOCKED_SCHEMES = setOf(
        "javascript", "file", "content", "data", "vbscript", "blob", "about", "view-source", "filesystem", "chrome",
    )
    private val MEDIA_EXTENSIONS = setOf("mp4", "webm", "m4v", "mkv", "mov", "mp3", "m4a", "ogg", "oga", "ogv", "wav", "flac", "aac")

    fun schemeOf(url: String): String? =
        SCHEME_REGEX.find(url.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    fun isWebUrl(url: String): Boolean = schemeOf(url) in WEB_SCHEMES

    fun isSecure(url: String): Boolean = schemeOf(url) == "https"

    fun isExternalScheme(url: String): Boolean = schemeOf(url) in EXTERNAL_SCHEMES

    fun isBlockedScheme(url: String): Boolean = schemeOf(url) in BLOCKED_SCHEMES

    fun resolve(input: String, engine: SearchEngine): ResolvedInput {
        val text = input.trim()
        if (text.isEmpty()) return ResolvedInput.Rejected(RejectReason.EMPTY)

        // Quick open for the embedded Rafiqi companion (local assets or online).
        val lower = text.lowercase(Locale.ROOT)
        if (lower == "رفيقي" || lower == "rafiqi" || lower == "rafiqi.hisn.local" ||
            lower == "rafiqi.local"
        ) {
            return ResolvedInput.Navigate(net.iegy.hisn.rafiqi.RafiqiUrls.HOME)
        }

        val looksLikeHostPort = HOST_PORT_REGEX.matches(text)
        val scheme = if (looksLikeHostPort) null else schemeOf(text)

        if (scheme != null) {
            return when {
                scheme in BLOCKED_SCHEMES -> ResolvedInput.Rejected(RejectReason.UNSUPPORTED_SCHEME)
                scheme in WEB_SCHEMES -> resolveWeb(text)
                scheme in EXTERNAL_SCHEMES -> ResolvedInput.External(text)
                // "note: buy milk" is a sentence, not a URL.
                text.any { it.isWhitespace() } -> search(text, engine)
                else -> ResolvedInput.Rejected(RejectReason.UNSUPPORTED_SCHEME)
            }
        }

        // Path-like text must never reach the WebView as a file path.
        if (text.any { it.isWhitespace() } || text.startsWith("/") || text.contains('\\')) {
            return search(text, engine)
        }

        val hostPart = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPart.count { it == ':' } > 1 || (hostPart.contains(':') && !looksLikeHostPort)) {
            return search(text, engine)
        }
        val host = hostPart.substringBefore(':')

        val isLocalhost = host.equals("localhost", ignoreCase = true)
        val isIpv4 = IPV4_REGEX.matches(host) && host.split('.').all { it.toInt() in 0..255 }
        val isDomain = DOMAIN_REGEX.matches(host)

        return when {
            isLocalhost || isIpv4 -> ResolvedInput.Navigate("http://$text")
            isDomain -> ResolvedInput.Navigate("https://$text")
            else -> search(text, engine)
        }
    }

    private fun resolveWeb(text: String): ResolvedInput {
        val afterScheme = text.substring(text.indexOf(':') + 1)
        if (!afterScheme.startsWith("//")) return ResolvedInput.Rejected(RejectReason.INVALID)
        val authority = afterScheme.removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostOf(text).isNullOrBlank() || authority.any { it.isWhitespace() }) {
            return ResolvedInput.Rejected(RejectReason.INVALID)
        }
        // Spaces in the path/query are encoded rather than rejected.
        return ResolvedInput.Navigate(text.replace(" ", "%20"))
    }

    private fun search(text: String, engine: SearchEngine): ResolvedInput =
        ResolvedInput.Search(engine.searchUrl(text), text)

    /** Lower-case host of an absolute URL, without userinfo or port. Null when there is no authority. */
    fun hostOf(url: String): String? {
        val authority = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://([^/?#]*)").find(url.trim())?.groupValues?.get(1)
            ?: return null
        val withoutUserInfo = authority.substringAfterLast('@')
        val host = if (withoutUserInfo.startsWith("[")) {
            withoutUserInfo.substringBefore(']') + "]"
        } else {
            withoutUserInfo.substringBefore(':')
        }
        return host.lowercase(Locale.ROOT).ifEmpty { null }
    }

    private val AUTHORITY_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*://)([^/?#]*)(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Removes "user:password@" from a URL so credentials are never persisted or displayed. */
    fun stripUserInfo(url: String): String {
        val match = AUTHORITY_REGEX.find(url) ?: return url
        val authority = match.groupValues[2]
        if (!authority.contains('@')) return url
        return match.groupValues[1] + authority.substringAfterLast('@') + match.groupValues[3]
    }

    /** Host without a leading "www.", used for compact display and allowlist entries. */
    fun displayHost(url: String): String? = hostOf(url)?.removePrefix("www.")

    /** True when the URL path ends in a common audio/video extension, i.e. it is a directly downloadable media file. */
    fun isDirectMedia(url: String): Boolean {
        if (!isWebUrl(url)) return false
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "")
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in MEDIA_EXTENSIONS
    }

    /** For user-entered shortcut/bookmark URLs: returns a safe http(s) URL or null. */
    fun normalizeWebUrl(input: String): String? =
        (resolve(input, SearchEngine.GOOGLE) as? ResolvedInput.Navigate)?.url
}
