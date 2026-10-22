package net.iegy.hisn.blocking

import java.net.URI
import java.util.Locale

/**
 * Bounded, deliberately conservative subset of Adblock Plus / EasyList syntax for Android WebView.
 * Supports domain-anchored network rules (||host^ and common path patterns), @@ exceptions,
 * $third-party / $domain= options, and common ## / #@# cosmetic selectors.
 * Unsupported syntax is ignored rather than guessed, to avoid breaking normal page resources.
 */
internal class FilterRuleEngine private constructor(
    private val networkRules: List<NetworkRule>,
    private val exceptions: List<NetworkRule>,
    private val cosmetic: List<CosmeticRule>,
) {
    val size: Int get() = networkRules.size + exceptions.size + cosmetic.size

    fun isException(url: String, pageHost: String?): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        val page = pageHost?.lowercase(Locale.ROOT)?.trimEnd('.')
        return host.isNotBlank() && exceptions.any { it.matches(url, host, page) }
    }

    fun shouldBlock(url: String, pageHost: String?): Boolean {
        if (isException(url, pageHost)) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (host.isBlank()) return false
        val page = pageHost?.lowercase(Locale.ROOT)?.trimEnd('.')
        return networkRules.any { it.matches(url, host, page) }
    }

    fun cosmeticScript(pageHost: String?): String {
        val host = pageHost?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return ""
        val selectors = LinkedHashSet<String>()
        val excluded = LinkedHashSet<String>()
        for (rule in cosmetic) {
            if (!rule.applies(host)) continue
            if (rule.exception) excluded += rule.selector else selectors += rule.selector
        }
        selectors.removeAll(excluded)
        val safe = selectors.filter { validSelector(it) }.take(MAX_COSMETIC_SELECTORS)
        if (safe.isEmpty()) return ""
        val json = safe.joinToString(prefix = "[", postfix = "]") { jsQuote(it) }
        // No page-supplied text is interpolated. CSS selectors are serialized as JSON string data.
        return """(function(){try{var s=$json;var id='__hisn_adblock_style';var css=s.map(function(x){return x+'{display:none!important;visibility:hidden!important}'}).join('\\n');var add=function(){try{var st=document.getElementById(id);if(!st){st=document.createElement('style');st.id=id;(document.head||document.documentElement).appendChild(st)}if(st.textContent!==css)st.textContent=css}catch(e){}};add();if(!window.__hisnAdObserver){window.__hisnAdObserver=new MutationObserver(function(){add()});window.__hisnAdObserver.observe(document.documentElement,{childList:true,subtree:true})}}catch(e){}})();"""
    }

    data class NetworkRule(
        val raw: String,
        val host: String?,
        val pattern: Regex?,
        val thirdParty: Boolean?,
        val includedDomains: Set<String>,
        val excludedDomains: Set<String>,
    ) {
        fun matches(url: String, requestHost: String, pageHost: String?): Boolean {
            if (host != null && !(requestHost == host || requestHost.endsWith(".$host"))) return false
            if (pattern != null && !pattern.containsMatchIn(url)) return false
            if (pageHost != null) {
                if (includedDomains.isNotEmpty() && includedDomains.none { pageHost == it || pageHost.endsWith(".$it") }) return false
                if (excludedDomains.any { pageHost == it || pageHost.endsWith(".$it") }) return false
                if (thirdParty != null) {
                    val same = requestHost == pageHost || requestHost.endsWith(".$pageHost") || pageHost.endsWith(".$requestHost")
                    if (thirdParty == same) return false
                }
            } else if (includedDomains.isNotEmpty() || excludedDomains.isNotEmpty() || thirdParty != null) return false
            return true
        }
    }

    data class CosmeticRule(val domains: List<String>, val selector: String, val exception: Boolean) {
        fun applies(host: String): Boolean {
            if (domains.isEmpty()) return true
            val excluded = domains.filter { it.startsWith("~") }.map { it.drop(1) }
            if (excluded.any { host == it || host.endsWith(".$it") }) return false
            val included = domains.filterNot { it.startsWith("~") }
            return included.isEmpty() || included.any { host == it || host.endsWith(".$it") }
        }
    }

    companion object {
        val EMPTY = FilterRuleEngine(emptyList(), emptyList(), emptyList())
        private const val MAX_NETWORK_RULES = 80_000
        private const val MAX_COSMETIC_RULES = 8_000
        private const val MAX_COSMETIC_SELECTORS = 1_200
        private val SAFE_SELECTOR = Regex("""^[a-zA-Z0-9_.*#:\[\]="'() >+~^$|,-]{1,220}$""")
        private val RESOURCE_OPTIONS = setOf("script", "image", "stylesheet", "xmlhttprequest", "subdocument", "font", "media", "object", "ping", "websocket", "other")

        fun parse(lines: Sequence<String>): FilterRuleEngine {
            val network = ArrayList<NetworkRule>()
            val exceptions = ArrayList<NetworkRule>()
            val cosmetics = ArrayList<CosmeticRule>()
            for (rawLine in lines) {
                if (network.size + exceptions.size >= MAX_NETWORK_RULES && cosmetics.size >= MAX_COSMETIC_RULES) break
                val line = rawLine.trim()
                val cosmetic = parseCosmetic(line)
                if (cosmetic != null) {
                    if (cosmetics.size < MAX_COSMETIC_RULES) cosmetics += cosmetic
                    continue
                }
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#")) continue
                if (line.startsWith("@@")) {
                    parseNetwork(line.removePrefix("@@"))?.let { if (exceptions.size < MAX_NETWORK_RULES) exceptions += it }
                } else {
                    parseNetwork(line)?.let { if (network.size < MAX_NETWORK_RULES) network += it }
                }
            }
            return FilterRuleEngine(network, exceptions, cosmetics)
        }

        private fun parseCosmetic(line: String): CosmeticRule? {
            val marker = when {
                "#@#" in line -> "#@#"
                "##" in line -> "##"
                else -> return null
            }
            val idx = line.indexOf(marker)
            if (idx < 0) return null
            val domainText = line.substring(0, idx)
            val selector = line.substring(idx + marker.length).trim()
            if (selector.isEmpty() || selector.length > 220 || !validSelector(selector)) return null
            val domains = if (domainText.isBlank()) emptyList() else domainText.split(',').map { it.trim().lowercase(Locale.ROOT) }
            if (domains.any { it.isBlank() || !it.removePrefix("~").matches(Regex("^[a-z0-9.-]+$")) }) return null
            return CosmeticRule(domains, selector, marker == "#@#")
        }

        private fun jsQuote(value: String): String = "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\""

        private fun validSelector(selector: String): Boolean {
            val normalized = selector.trim().lowercase(Locale.ROOT)
            if (normalized in setOf("*", "html", "body", ":root", "body *", "html *")) return false
            if (listOf(":has-text(", ":matches-css(", ":xpath(", ":remove(", ":upward(", ":contains(").any(normalized::contains)) return false
            return SAFE_SELECTOR.matches(selector) && !selector.contains("url(", true) && !selector.contains("javascript", true)
        }

        /** Converts only the common EasyList wildcard and separator tokens; rejects malformed/complex suffixes. */
        private fun adblockSuffixToRegex(suffix: String): String? {
            if (suffix.length > 180) return null
            val out = StringBuilder()
            for (ch in suffix) {
                when (ch) {
                    '*' -> out.append(".*")
                    '^' -> out.append("(?:[^a-zA-Z0-9_.%-]|$)")
                    else -> {
                        if (ch.isLetterOrDigit() || ch in "/?&=._%+-#~:@") out.append(ch)
                        else return null
                    }
                }
            }
            return out.toString()
        }

        private fun parseNetwork(line: String): NetworkRule? {
            if (line.isBlank() || line.startsWith("/") || line.contains("##") || line.contains("#@#")) return null
            val pieces = line.split('$', limit = 2)
            val body = pieces[0]
            if (body.isBlank()) return null
            val options = pieces.getOrNull(1)?.split(',') ?: emptyList()
            if (options.any { it.startsWith("~") && it.drop(1) !in RESOURCE_OPTIONS && it != "~third-party" }) return null
            // Keep this implementation intentionally conservative: only common, well-defined options.
            // Resource-type options are skipped until request-type mapping is available on every WebView version.
            if (options.any { it.substringBefore('=').removePrefix("~") in RESOURCE_OPTIONS }) return null
            val knownOptions = setOf("third-party", "domain", "badfilter")
            if (options.any { it.substringBefore('=').removePrefix("~") !in knownOptions }) return null
            if ("badfilter" in options) return null
            val domainOption = options.firstOrNull { it.startsWith("domain=") }?.substringAfter('=')
            val include = domainOption?.split('|')?.filter { it.isNotBlank() && !it.startsWith("~") }?.map { it.lowercase(Locale.ROOT) }?.toSet().orEmpty()
            val exclude = domainOption?.split('|')?.filter { it.startsWith("~") }?.map { it.drop(1).lowercase(Locale.ROOT) }?.toSet().orEmpty()
            val third = when {
                "third-party" in options -> true
                "~third-party" in options -> false
                else -> null
            }
            val anchoredHost = if (body.startsWith("||")) body.drop(2).takeWhile { it !in "^/*?" }.lowercase(Locale.ROOT).takeIf { it.contains('.') && it.matches(Regex("^[a-z0-9.-]+$")) } else null
            val pattern = when {
                body == "*" -> null
                body.startsWith("||") && anchoredHost != null -> {
                    val suffix = body.substring(2 + anchoredHost.length)
                    when {
                        suffix.isEmpty() || suffix == "^" -> null
                        suffix.startsWith("/") -> {
                            val converted = adblockSuffixToRegex(suffix) ?: return null
                            Regex("^https?://(?:[a-z0-9-]+\\.)*${Regex.escape(anchoredHost)}(?::[0-9]+)?$converted", RegexOption.IGNORE_CASE)
                        }
                        else -> return null
                    }
                }
                body.startsWith("|") && body.endsWith("|") && body.length <= 260 -> Regex("^" + Regex.escape(body.removePrefix("|").removeSuffix("|")) + "$", RegexOption.IGNORE_CASE)
                body.startsWith("||") && anchoredHost == null -> return null
                body.length <= 180 && !body.contains("^") && !body.contains("*") && body.contains('/') -> Regex.escape(body).toRegex(RegexOption.IGNORE_CASE)
                else -> null
            }
            if (anchoredHost == null && pattern == null) return null
            return NetworkRule(line, anchoredHost, pattern, third, include, exclude)
        }
    }
}
