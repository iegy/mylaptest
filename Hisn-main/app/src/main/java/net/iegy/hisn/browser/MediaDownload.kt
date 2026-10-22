package net.iegy.hisn.browser


/**
 * Lightweight media download support for personal offline use.
 *
 * - Direct file URLs (mp4, webm, …) are always offered.
 * - On open pages we also look for &lt;video&gt; / &lt;source&gt; / og:video and for media
 *   requests the WebView has already loaded.
 * - YouTube, TikTok, Instagram and Facebook often serve adaptive / DRM-protected streams
 *   that cannot be extracted from WebView alone. In those cases the UI shows an honest
 *   message instead of claiming a download that will fail.
 *
 * This is intentionally not a full yt-dlp / NewPipe replacement (GPL extractors are not
 * linked; the app stays free of that dependency).
 */
enum class MediaPlatform {
    YOUTUBE,
    TIKTOK,
    INSTAGRAM,
    FACEBOOK,
    TWITTER,
    GENERIC,
}

data class MediaCandidate(
    val url: String,
    val label: String,
    val mimeHint: String? = null,
    val isLikelyAudioOnly: Boolean = false,
)

object MediaDownload {

    private val YT_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
        "www.youtu.be", "music.youtube.com",
    )
    private val TIKTOK_HOSTS = setOf(
        "tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com",
    )
    private val IG_HOSTS = setOf(
        "instagram.com", "www.instagram.com", "m.instagram.com",
    )
    private val FB_HOSTS = setOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch", "fb.com", "www.fb.com",
    )
    private val TW_HOSTS = setOf(
        "twitter.com", "www.twitter.com", "mobile.twitter.com", "x.com", "www.x.com",
    )

    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "ogg", "wav", "opus")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    /** Host of [pageUrl] without leading "www.". */
    fun platformOf(pageUrl: String): MediaPlatform {
        val host = UrlResolver.hostOf(pageUrl)?.lowercase()?.removePrefix("www.") ?: return MediaPlatform.GENERIC
        return when {
            host in YT_HOSTS || host.endsWith(".youtube.com") || host == "youtu.be" -> MediaPlatform.YOUTUBE
            host in TIKTOK_HOSTS || host.endsWith(".tiktok.com") -> MediaPlatform.TIKTOK
            host in IG_HOSTS || host.endsWith(".instagram.com") -> MediaPlatform.INSTAGRAM
            host in FB_HOSTS || host.endsWith(".facebook.com") || host == "fb.watch" -> MediaPlatform.FACEBOOK
            host in TW_HOSTS || host.endsWith(".twitter.com") || host == "x.com" -> MediaPlatform.TWITTER
            else -> MediaPlatform.GENERIC
        }
    }

    fun isKnownStreamingPlatform(pageUrl: String): Boolean =
        platformOf(pageUrl) != MediaPlatform.GENERIC

    /**
     * True when the URL path looks like a downloadable media file
     * (same rules as [isDirectMediaUrl], plus audio/image for the picker).
     */
    fun isDownloadableMediaUrl(url: String): Boolean {
        if (!UrlResolver.isWebUrl(url)) return false
        if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) return false
        val path = url.substringBefore('#').substringBefore('?')
        val fileName = path.substringAfterLast('/')
        if (!fileName.contains('.')) return false
        val ext = fileName.substringAfterLast('.').lowercase()
        return ext in VIDEO_EXTS || ext in AUDIO_EXTS || ext in IMAGE_EXTS
    }

    /**
     * JavaScript that returns a JSON array of {url, kind, label} found in the current page.
     * Safe to run even when the site is hostile: it only reads DOM / meta tags.
     */
    val FIND_STREAMS_JS: String = """
        (function(){
          var out = [];
          var seen = {};
          function add(u, kind, label) {
            if (!u || typeof u !== 'string') return;
            u = u.trim();
            if (!u || u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
            if (seen[u]) return;
            seen[u] = 1;
            out.push({url: u, kind: kind || 'video', label: label || ''});
          }
          try {
            document.querySelectorAll('video').forEach(function(v) {
              if (v.currentSrc) add(v.currentSrc, 'video', 'video');
              if (v.src) add(v.src, 'video', 'video');
              v.querySelectorAll('source').forEach(function(s) {
                add(s.src, (s.type || '').indexOf('audio') >= 0 ? 'audio' : 'video', s.type || '');
              });
            });
            document.querySelectorAll('audio').forEach(function(a) {
              if (a.currentSrc) add(a.currentSrc, 'audio', 'audio');
              if (a.src) add(a.src, 'audio', 'audio');
              a.querySelectorAll('source').forEach(function(s) { add(s.src, 'audio', s.type || ''); });
            });
            document.querySelectorAll('meta[property="og:video"], meta[property="og:video:secure_url"], meta[property="og:video:url"]').forEach(function(m) {
              add(m.getAttribute('content'), 'video', 'og:video');
            });
            document.querySelectorAll('meta[property="og:audio"], meta[property="og:audio:url"]').forEach(function(m) {
              add(m.getAttribute('content'), 'audio', 'og:audio');
            });
            document.querySelectorAll('a[href]').forEach(function(a) {
              var h = a.href || '';
              if (/\.(mp4|webm|mkv|m4v|mov|mp3|m4a|aac|ogg|wav)(\?|#|$)/i.test(h)) add(h, 'file', a.textContent || 'link');
            });
          } catch (e) {}
          return JSON.stringify(out);
        })();
    """.trimIndent()

    /** Parse the JSON produced by [FIND_STREAMS_JS]. */
    fun parseJsResult(json: String?): List<MediaCandidate> {
        if (json.isNullOrBlank() || json == "null") return emptyList()
        val trimmed = json.trim()
        val attempts = ArrayList<String>(3)
        attempts.add(trimmed)
        decodeJsonStringLiteral(trimmed)?.let { attempts.add(it) }
        attempts.toList().forEach { a ->
            decodeJsonStringLiteral(a)?.let { if (it !in attempts) attempts.add(it) }
        }
        for (raw in attempts) {
            val list = candidatesFromJsonArray(raw)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    /**
     * Decode a JSON string literal ("...") without org.json, so unit tests on the plain JVM
     * (android.jar stubs) still work. Handles \\ \" \/ \n \r \t and \uXXXX.
     */
    private fun decodeJsonStringLiteral(s: String): String? {
        val t = s.trim()
        if (t.length < 2 || t.first() != '"' || t.last() != '"') return null
        val out = StringBuilder(t.length)
        var i = 1
        val end = t.length - 1
        while (i < end) {
            val c = t[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= end) return null
            when (val e = t[i + 1]) {
                '"', '\\', '/' -> { out.append(e); i += 2 }
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'u' -> {
                    if (i + 5 >= end) return null
                    val hex = t.substring(i + 2, i + 6)
                    val code = hex.toIntOrNull(16) ?: return null
                    out.append(code.toChar())
                    i += 6
                }
                else -> return null
            }
        }
        return out.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Minimal array parser for [{"url":"...","kind":"...","label":"..."}, ...]
     * Avoids org.json so unit tests do not hit Android stub implementations.
     */
    private fun candidatesFromJsonArray(raw: String): List<MediaCandidate> {
        val t = raw.trim()
        if (!t.startsWith("[")) return emptyList()
        val out = ArrayList<MediaCandidate>()
        // Extract every "url":"..." value (no org.json — works on the JVM unit-test classpath).
        val urlRegex = Regex("\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (m in urlRegex.findAll(t)) {
            val url = unescapeJsonChars(m.groupValues[1])
            if (url.isEmpty()) continue
            if (!isDownloadableMediaUrl(url) && !looksLikeCdnMedia(url)) continue
            out.add(
                MediaCandidate(
                    url = url,
                    label = if (extensionOf(url) in AUDIO_EXTS) "Audio" else "Video",
                    mimeHint = guessMime(url, null),
                    isLikelyAudioOnly = extensionOf(url) in AUDIO_EXTS,
                ),
            )
        }
        return out.distinctBy { it.url }
    }

    private fun unescapeJsonChars(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                out.append(c)
                i++
                continue
            }
            when (val e = s[i + 1]) {
                '"', '\\', '/' -> { out.append(e); i += 2 }
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'u' -> {
                    if (i + 5 < s.length) {
                        val hex = s.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            out.append(code.toChar())
                            i += 6
                            continue
                        }
                    }
                    out.append(e)
                    i += 2
                }
                else -> { out.append(e); i += 2 }
            }
        }
        return out.toString()
    }

    /**
     * Merge page candidates with URLs the engine observed while the page was loading
     * (progressive mp4 / webm segments are often visible this way).
     */
    fun mergeCandidates(
        fromPage: List<MediaCandidate>,
        observedUrls: Collection<String>,
        pageUrl: String,
    ): List<MediaCandidate> {
        val seen = linkedSetOf<String>()
        val out = ArrayList<MediaCandidate>()
        fun add(c: MediaCandidate) {
            if (c.url in seen) return
            if (!UrlResolver.isWebUrl(c.url)) return
            if (c.url.startsWith("blob:", ignoreCase = true)) return
            seen += c.url
            out += c
        }
        fromPage.forEach(::add)
        for (u in observedUrls) {
            if (isDownloadableMediaUrl(u) || looksLikeCdnMedia(u)) {
                add(
                    MediaCandidate(
                        url = u,
                        label = labelForUrl(u),
                        mimeHint = guessMime(u, null),
                        isLikelyAudioOnly = extensionOf(u) in AUDIO_EXTS,
                    ),
                )
            }
        }
        // Prefer progressive files that look complete over tiny segment-looking paths.
        return out.sortedWith(
            compareBy<MediaCandidate> { it.isLikelyAudioOnly }
                .thenByDescending { score(it.url) },
        )
    }

    fun suggestedFileName(url: String, pageTitle: String?): String {
        val ext = extensionOf(url)?.takeIf { it.isNotBlank() } ?: "mp4"
        val pathName = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        if (pathName.contains('.') && !pathName.startsWith("download", ignoreCase = true)) {
            return FileNameSafe.sanitize(pathName)
        }
        val base = pageTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: UrlResolver.hostOf(url)
            ?: "media"
        return FileNameSafe.sanitize("$base.$ext")
    }

    // ---- internals ----------------------------------------------------------


    /** CDN paths without a classic extension still often end with .mp4 or carry video mime in query. */
    private fun looksLikeCdnMedia(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("mime=video") || lower.contains("mime_type=video")) return true
        if (lower.contains("/video/") && (lower.contains(".mp4") || lower.contains("format=mp4"))) return true
        return false
    }

    private fun extensionOf(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return null
        return name.substringAfterLast('.').lowercase().takeIf { it.length in 2..5 }
    }

    private fun guessMime(url: String, kind: String?): String? {
        val ext = extensionOf(url)
        return when {
            ext in VIDEO_EXTS -> "video/$ext"
            ext in AUDIO_EXTS -> "audio/$ext"
            ext in IMAGE_EXTS -> "image/$ext"
            kind == "audio" -> "audio/mp4"
            kind == "video" -> "video/mp4"
            else -> null
        }
    }

    private fun labelForUrl(url: String): String {
        val ext = extensionOf(url)?.uppercase()
        return when {
            ext in VIDEO_EXTS.map { it.uppercase() } -> "Video ($ext)"
            ext in AUDIO_EXTS.map { it.uppercase() } -> "Audio ($ext)"
            ext in IMAGE_EXTS.map { it.uppercase() } -> "Image ($ext)"
            else -> "Media"
        }
    }

    private fun score(url: String): Int {
        var s = 0
        val lower = url.lowercase()
        if (lower.contains(".mp4")) s += 5
        if (lower.contains(".webm")) s += 4
        if (lower.contains("googlevideo") || lower.contains("tiktokcdn") || lower.contains("fbcdn")) s += 2
        if (lower.contains("segment") || lower.contains("init.mp4") || lower.contains("/range/")) s -= 3
        return s
    }
}

/** Thin wrapper so MediaDownload does not depend on the download package. */
internal object FileNameSafe {
    private val BAD = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    fun sanitize(name: String): String {
        var n = name.replace(BAD, "_").trim().trim('.')
        if (n.isBlank()) n = "media"
        if (n.length > 120) {
            val ext = n.substringAfterLast('.', "")
            val base = n.substringBeforeLast('.', n).take(100)
            n = if (ext.isNotEmpty() && ext.length <= 8) "$base.$ext" else base.take(120)
        }
        return n
    }
}
