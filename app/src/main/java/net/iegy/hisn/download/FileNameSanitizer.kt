package net.iegy.hisn.download

/**
 * Makes a server-supplied or URL-derived file name safe to use as a local file name.
 * Removes any directory component (blocking path traversal), control and reserved characters,
 * leading dots, and over-long names, while preserving Unicode (e.g. Arabic) text and the extension.
 */
object FileNameSanitizer {
    private const val MAX_LENGTH = 120
    private val ILLEGAL = Regex("[\\u0000-\\u001F\\u007F<>:\"/\\\\|?*]")
    private val WHITESPACE = Regex("\\s+")

    fun sanitize(raw: String?, fallback: String = "download"): String {
        var name = raw.orEmpty()
        // Drop any path the server or URL tried to smuggle in.
        name = name.substringAfterLast('/').substringAfterLast('\\')
        name = ILLEGAL.replace(name, "_")
        name = WHITESPACE.replace(name, " ").trim()
        name = name.trimStart('.').trimEnd('.', ' ')
        if (name.isEmpty() || name.all { it == '_' }) return fallback

        if (name.length > MAX_LENGTH) {
            val dot = name.lastIndexOf('.')
            val ext = if (dot > 0 && name.length - dot <= 10) name.substring(dot) else ""
            name = name.take(MAX_LENGTH - ext.length) + ext
        }
        return name
    }

    /** Returns [name], or "name (1).ext", "name (2).ext"... for the first variant for which [exists] is false. */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        for (i in 1..999) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }
}
