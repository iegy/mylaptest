package net.iegy.hisn.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.iegy.hisn.browser.SearchEngine
import net.iegy.hisn.browser.UrlResolver
import net.iegy.hisn.settings.AppLanguage
import net.iegy.hisn.settings.NewTabPage
import net.iegy.hisn.settings.SettingsRepository
import net.iegy.hisn.settings.StartupBehavior
import net.iegy.hisn.settings.ThemeMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The file is not a Hisn backup, is damaged, or comes from a newer app version. */
class BackupException(message: String) : Exception(message)

/** What a restore actually added (items that already existed are not counted). */
data class BackupSummary(
    val folders: Int,
    val bookmarks: Int,
    val shortcuts: Int,
    val history: Int,
    val languageChanged: Boolean,
)

/**
 * Exports bookmarks, folders, shortcuts, history and settings to one JSON file (.hisn) and merges such a file back.
 *
 * Restoring never deletes anything: existing bookmarks, shortcuts and history entries are kept and only missing
 * ones are added. Only http(s) addresses are accepted from a file, so a crafted backup cannot plant javascript: or
 * file: links. Downloads, open tabs and the filter lists are deliberately not part of a backup.
 * Regular process only (the private process has no database).
 */
class BackupManager(
    private val bookmarks: BookmarkDao,
    private val history: HistoryDao,
    private val shortcuts: ShortcutDao,
    private val settingsRepo: SettingsRepository,
) {

    suspend fun write(output: OutputStream) {
        val json = createBackup()
        withContext(Dispatchers.IO) {
            output.write(json.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    suspend fun restore(input: InputStream): BackupSummary {
        val text = withContext(Dispatchers.IO) { readLimited(input) }
        return restore(text)
    }

    suspend fun createBackup(now: Long = System.currentTimeMillis()): String {
        val s = settingsRepo.settings.first()
        val folderRows = bookmarks.getFolders()
        val bookmarkRows = bookmarks.getAll()
        val shortcutRows = shortcuts.getAll()
        val historyRows = history.getAll()
        return withContext(Dispatchers.Default) {
            JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("createdAt", now)
                put(
                    "settings",
                    JSONObject().apply {
                        put("theme", s.theme.name)
                        put("language", s.language.name)
                        put("searchEngine", s.searchEngine.id)
                        put("blockingEnabled", s.blockingEnabled)
                        put("autoUpdateFilters", s.autoUpdateFilters)
                        put("allowlist", JSONArray(s.allowlist.toList()))
                        put("newTabPage", s.newTabPage.name)
                        put("customHomeUrl", s.customHomeUrl)
                        put("startup", s.startup.name)
                        put("desktopByDefault", s.desktopByDefault)
                        put("showRecent", s.showRecent)
                        put("textZoom", s.textZoom)
                        put("httpsOnly", s.httpsOnly)
                        put("jsBlockedHosts", JSONArray(s.jsBlockedHosts.toList()))
                    },
                )
                put(
                    "folders",
                    JSONArray().also { arr ->
                        folderRows.forEach { f ->
                            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("createdAt", f.createdAt))
                        }
                    },
                )
                put(
                    "bookmarks",
                    JSONArray().also { arr ->
                        bookmarkRows.forEach { b ->
                            val o = JSONObject().put("title", b.title).put("url", b.url).put("createdAt", b.createdAt)
                            if (b.folderId != null) o.put("folderId", b.folderId)
                            arr.put(o)
                        }
                    },
                )
                put(
                    "shortcuts",
                    JSONArray().also { arr ->
                        shortcutRows.sortedBy { it.position }.forEach { sc ->
                            arr.put(JSONObject().put("title", sc.title).put("url", sc.url))
                        }
                    },
                )
                put(
                    "history",
                    JSONArray().also { arr ->
                        historyRows.forEach { h ->
                            arr.put(JSONObject().put("url", h.url).put("title", h.title).put("visitedAt", h.visitedAt))
                        }
                    },
                )
            }.toString()
        }
    }

    suspend fun restore(text: String, now: Long = System.currentTimeMillis()): BackupSummary {
        val root = withContext(Dispatchers.Default) {
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw BackupException("Not a JSON document")
            }
        }
        if (root.optString("format") != FORMAT) throw BackupException("Not a Hisn backup")
        if (root.optInt("version", 0) !in 1..VERSION) throw BackupException("Unsupported backup version")

        // Folders: reuse a folder with the same name, otherwise create it. Maps the file's ids to local ids.
        val folderIds = HashMap<Long, Long>()
        val foldersByName = bookmarks.getFolders().associateByTo(HashMap<String, FolderEntity>()) { it.name.lowercase() }
        var addedFolders = 0
        root.optJSONArray("folders")?.forEachObject { o ->
            val name = o.optString("name").trim().take(100)
            if (name.isEmpty()) return@forEachObject
            val existing = foldersByName[name.lowercase()]
            val localId = existing?.id ?: run {
                val folder = FolderEntity(name = name, createdAt = o.optLong("createdAt", now))
                val id = bookmarks.insertFolder(folder)
                foldersByName[name.lowercase()] = folder.copy(id = id)
                addedFolders++
                id
            }
            if (o.has("id")) folderIds[o.optLong("id")] = localId
        }

        // Bookmarks: the address is the identity (the same rule the bookmark button uses).
        val knownBookmarkUrls = bookmarks.getAll().mapTo(HashSet()) { it.url }
        var addedBookmarks = 0
        root.optJSONArray("bookmarks")?.forEachObject { o ->
            val url = cleanUrl(o.optString("url")) ?: return@forEachObject
            if (!knownBookmarkUrls.add(url)) return@forEachObject
            val folderId = if (o.has("folderId")) folderIds[o.optLong("folderId")] else null
            bookmarks.insert(
                BookmarkEntity(
                    title = o.optString("title").ifBlank { url }.take(300),
                    url = url,
                    folderId = folderId,
                    createdAt = o.optLong("createdAt", now),
                ),
            )
            addedBookmarks++
        }

        // Shortcuts: appended after the existing ones, in the file's order.
        val existingShortcuts = shortcuts.getAll()
        val knownShortcutUrls = existingShortcuts.mapTo(HashSet()) { it.url }
        var nextPosition = (existingShortcuts.maxOfOrNull { it.position } ?: -1) + 1
        val newShortcuts = ArrayList<ShortcutEntity>()
        root.optJSONArray("shortcuts")?.forEachObject { o ->
            val url = cleanUrl(o.optString("url")) ?: return@forEachObject
            if (!knownShortcutUrls.add(url)) return@forEachObject
            newShortcuts += ShortcutEntity(title = o.optString("title").ifBlank { url }.take(60), url = url, position = nextPosition++)
        }
        if (newShortcuts.isNotEmpty()) shortcuts.insertAll(newShortcuts)

        // History: a visit is identified by address + time. Inserted in one batch for speed.
        val knownVisits = history.getAll().mapTo(HashSet()) { it.url to it.visitedAt }
        val newVisits = ArrayList<HistoryEntity>()
        root.optJSONArray("history")?.forEachObject { o ->
            val url = cleanUrl(o.optString("url")) ?: return@forEachObject
            val visitedAt = o.optLong("visitedAt", -1)
            if (visitedAt < 0 || !knownVisits.add(url to visitedAt)) return@forEachObject
            newVisits += HistoryEntity(url = url, title = o.optString("title").take(300), visitedAt = visitedAt)
        }
        if (newVisits.isNotEmpty()) history.insertAll(newVisits)

        val languageChanged = root.optJSONObject("settings")?.let { applySettings(it) } ?: false
        return BackupSummary(addedFolders, addedBookmarks, newShortcuts.size, newVisits.size, languageChanged)
    }

    /** Applies every setting present in the file with a valid value. Returns true when the language changed. */
    private suspend fun applySettings(o: JSONObject): Boolean {
        val before = settingsRepo.settings.first().language
        enumOf<ThemeMode>(o, "theme")?.let { settingsRepo.setTheme(it) }
        enumOf<AppLanguage>(o, "language")?.let { settingsRepo.setLanguage(it) }
        if (o.has("searchEngine")) settingsRepo.setSearchEngine(SearchEngine.fromId(o.optString("searchEngine")))
        if (o.has("blockingEnabled")) settingsRepo.setBlockingEnabled(o.optBoolean("blockingEnabled", true))
        if (o.has("autoUpdateFilters")) settingsRepo.setAutoUpdateFilters(o.optBoolean("autoUpdateFilters", true))
        o.optJSONArray("allowlist")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { settingsRepo.addToAllowlist(it) }
        }
        enumOf<NewTabPage>(o, "newTabPage")?.let { settingsRepo.setNewTabPage(it) }
        if (o.has("customHomeUrl")) {
            settingsRepo.setCustomHomeUrl(UrlResolver.normalizeWebUrl(o.optString("customHomeUrl")).orEmpty())
        }
        enumOf<StartupBehavior>(o, "startup")?.let { settingsRepo.setStartup(it) }
        if (o.has("desktopByDefault")) settingsRepo.setDesktopByDefault(o.optBoolean("desktopByDefault", false))
        if (o.has("showRecent")) settingsRepo.setShowRecent(o.optBoolean("showRecent", true))
        if (o.has("textZoom")) settingsRepo.setTextZoom(o.optInt("textZoom", 100))
        if (o.has("httpsOnly")) settingsRepo.setHttpsOnly(o.optBoolean("httpsOnly", false))
        o.optJSONArray("jsBlockedHosts")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { settingsRepo.addJsBlocked(it) }
        }
        return settingsRepo.settings.first().language != before
    }

    private fun cleanUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty() || url.length > MAX_URL_LENGTH || !UrlResolver.isWebUrl(url)) return null
        return UrlResolver.stripUserInfo(url)
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) throw BackupException("Backup file is too large")
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private inline fun <reified T : Enum<T>> enumOf(o: JSONObject, key: String): T? {
        if (!o.has(key)) return null
        val name = o.optString(key)
        return enumValues<T>().firstOrNull { it.name == name }
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let { action(it) }
    }

    private companion object {
        const val FORMAT = "hisn-backup"
        const val VERSION = 1
        const val MAX_URL_LENGTH = 4096
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}
