package net.iegy.hisn.blocking

import java.util.Locale

/**
 * Immutable set of blocked domains with parent-domain matching:
 * if "doubleclick.net" is listed, "ad.doubleclick.net" is blocked too.
 * Lookups are a handful of hash probes, so they are safe to run on WebView's request thread.
 */
class DomainMatcher(private val domains: Set<String>) {
    val size: Int get() = domains.size

    fun isBlocked(host: String): Boolean {
        var h = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (h.isEmpty()) return false
        while (true) {
            if (h in domains) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            // Never match a bare TLD such as "com".
            if (!h.contains('.')) return false
        }
    }

    companion object {
        val EMPTY = DomainMatcher(emptySet())

        private val IGNORED_HOSTS = setOf(
            "localhost", "localhost.localdomain", "local", "broadcasthost",
            "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
            "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
        )
        private val SINKHOLE_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
        private val VALID_HOST = Regex("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$")

        /**
         * Parses either hosts-file format ("0.0.0.0 ads.example.com", used by the default list)
         * or a plain one-domain-per-line list. Comments and malformed lines are skipped.
         */
        fun parse(lines: Sequence<String>): DomainMatcher {
            val set = HashSet<String>(1 shl 14)
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val tokens = line.split(Regex("\\s+"))
                val candidate = when {
                    tokens.size == 1 -> tokens[0]
                    tokens[0] in SINKHOLE_ADDRESSES -> tokens[1]
                    else -> continue
                }.lowercase(Locale.ROOT).trimEnd('.')
                if (candidate in IGNORED_HOSTS || !candidate.contains('.')) continue
                if (!VALID_HOST.matches(candidate)) continue
                set.add(candidate)
            }
            return DomainMatcher(set)
        }
    }
}

/** Per-site "don't block here" list. Entries cover the host and all its subdomains. */
object Allowlist {
    fun normalize(host: String): String =
        host.trim().trimEnd('.').lowercase(Locale.ROOT).removePrefix("www.")

    fun isAllowed(entries: Set<String>, pageHost: String?): Boolean {
        if (pageHost == null || entries.isEmpty()) return false
        val h = normalize(pageHost)
        return entries.any { entry ->
            val e = normalize(entry)
            e.isNotEmpty() && (h == e || h.endsWith(".$e"))
        }
    }
}

/** First-party detection, so ads served from the page's own site are never broken by a request-level rule. */
object SitePolicy {
    fun isSameSite(requestHost: String, pageHost: String): Boolean {
        val a = Allowlist.normalize(requestHost)
        val b = Allowlist.normalize(pageHost)
        return a == b || a.endsWith(".$b") || b.endsWith(".$a")
    }
}
