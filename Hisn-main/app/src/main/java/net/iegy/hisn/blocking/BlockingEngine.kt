package net.iegy.hisn.blocking

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface FilterUpdate {
    data class Updated(val rules: Int) : FilterUpdate
    data object Failed : FilterUpdate
}

/** Multi-layer ad/tracker filtering for WebView: hosts rules + a conservative EasyList syntax subset + cosmetic rules. */
class BlockingEngine(private val context: Context) {
    @Volatile private var matcher: DomainMatcher = DomainMatcher.EMPTY
    @Volatile private var advancedRules: FilterRuleEngine = FilterRuleEngine.EMPTY
    @Volatile private var enabled: Boolean = true
    @Volatile private var allowlist: Set<String> = emptySet()

    private val _ruleCount = MutableStateFlow(0)
    val ruleCount: StateFlow<Int> = _ruleCount.asStateFlow()

    private val cacheDir: File get() = File(context.filesDir, "filters").apply { mkdirs() }
    private fun cache(name: String) = File(cacheDir, name)

    fun configure(enabled: Boolean, allowlist: Set<String>) {
        this.enabled = enabled
        this.allowlist = allowlist.map(Allowlist::normalize).filter(String::isNotBlank).toSet()
    }

    /** Loads the last valid cached lists, falling back to the bundled offline hosts list. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val hostRules = runCatching {
            cache(HOST_CACHE).takeIf { it.isFile }?.useLines { DomainMatcher.parse(it) }
                ?.takeIf { it.size >= MIN_CACHED_HOST_RULES }
        }.getOrNull() ?: context.assets.open(ASSET).bufferedReader().useLines { DomainMatcher.parse(it) }
        val filterLines = sequenceOf(EASYLIST_CACHE, EASYPRIVACY_CACHE).flatMap { name ->
            val file = cache(name)
            if (file.isFile && file.length() <= MAX_BYTES) runCatching { file.readLines().asSequence() }.getOrDefault(emptySequence())
            else emptySequence()
        }.toList()
        val parsed = FilterRuleEngine.parse(filterLines.asSequence())
        matcher = hostRules
        advancedRules = parsed
        _ruleCount.value = hostRules.size + parsed.size
    }

    /** Compatibility overload for callers/tests that only have a hostname. */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean = shouldBlock("https://${requestHost.orEmpty()}/", requestHost, pageHost)

    /** True when a subresource URL matches a block rule; main-frame navigation is filtered by the caller. */
    fun shouldBlock(requestUrl: String, requestHost: String?, pageHost: String?): Boolean {
        if (!enabled || requestHost.isNullOrBlank()) return false
        if (pageHost != null && Allowlist.isAllowed(allowlist, pageHost)) return false
        if (advancedRules.isException(requestUrl, pageHost)) return false
        val sameSite = pageHost != null && SitePolicy.isSameSite(requestHost, pageHost)
        // Hosts lists are intentionally third-party only; explicit EasyList URL rules may still block same-site ads.
        return (!sameSite && matcher.isBlocked(requestHost)) || advancedRules.shouldBlock(requestUrl, pageHost)
    }

    /** Returns safe cosmetic CSS injection for a page host. The script is generated from parsed selectors only. */
    fun cosmeticScript(pageHost: String?): String = if (enabled && pageHost != null && !Allowlist.isAllowed(allowlist, pageHost)) {
        advancedRules.cosmeticScript(pageHost)
    } else ""

    /** Updates the hosts list and EasyList/EasyPrivacy. Each list is validated and atomically replaced independently. */
    suspend fun update(): FilterUpdate = withContext(Dispatchers.IO) {
        var updatedAny = false
        val hosts = downloadAndParseHosts(SOURCE_URL)
        if (hosts != null) {
            matcher = hosts
            updatedAny = true
        }
        val easyList = downloadText(EASYLIST_URL)?.takeIf { validAdvancedList(it) }
        if (easyList != null && saveAtomically(EASYLIST_CACHE, easyList)) updatedAny = true
        val easyPrivacy = downloadText(EASYPRIVACY_URL)?.takeIf { validAdvancedList(it) }
        if (easyPrivacy != null && saveAtomically(EASYPRIVACY_CACHE, easyPrivacy)) updatedAny = true

        // Reload both previously cached and newly downloaded advanced lists; an individual list failure keeps its old copy.
        val lines = sequenceOf(EASYLIST_CACHE, EASYPRIVACY_CACHE).flatMap { name ->
            val f = cache(name)
            if (f.isFile && f.length() <= MAX_BYTES) f.readLines().asSequence() else emptySequence()
        }.toList()
        val parsed = FilterRuleEngine.parse(lines.asSequence())
        advancedRules = parsed
        _ruleCount.value = matcher.size + parsed.size
        if (updatedAny) FilterUpdate.Updated(_ruleCount.value) else FilterUpdate.Failed
    }

    private fun validAdvancedList(text: String): Boolean =
        text.count { it == '\n' } >= MIN_FILTER_LINES &&
            FilterRuleEngine.parse(text.lineSequence()).size >= MIN_ADVANCED_RULES

    private fun downloadAndParseHosts(url: String): DomainMatcher? {
        val text = downloadText(url) ?: return null
        val parsed = DomainMatcher.parse(text.lineSequence())
        if (parsed.size < MIN_UPDATE_RULES) return null
        if (!saveAtomically(HOST_CACHE, text)) return null
        return parsed
    }

    private fun downloadText(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HISN-Browser-FilterUpdate/2.0")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("Filter list too large")
                    output.write(buffer, 0, read)
                }
            }
            // نفكّ الترميز مرة واحدة بعد جمع كل البايتات، حتى لا يتشوّه حرف UTF-8 انقسم بين قطعتين.
            String(output.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun saveAtomically(targetName: String, text: String): Boolean {
        val target = cache(targetName)
        val temp = File(cacheDir, "$targetName.tmp")
        return try {
            temp.writeText(text, Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    companion object {
        private const val ASSET = "filters/default_hosts.txt"
        private const val HOST_CACHE = "hosts.txt"
        private const val EASYLIST_CACHE = "easylist.txt"
        private const val EASYPRIVACY_CACHE = "easyprivacy.txt"
        const val SOURCE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
        const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val MAX_BYTES = 16L * 1024 * 1024
        private const val MIN_UPDATE_RULES = 5_000
        private const val MIN_CACHED_HOST_RULES = 1_000
        private const val MIN_FILTER_LINES = 1_000
        private const val MIN_ADVANCED_RULES = 50
    }
}
