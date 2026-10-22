package net.iegy.hisn.rafiqi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exposed to the local Rafiqi WebView as `window.HisnRafiqi`.
 *
 * The web app (or a small injected helper) can call these methods to schedule
 * exact prayer alarms and query permission state. All work is delegated to
 * [PrayerScheduler] so the bridge stays thin.
 *
 * Security: this interface is added **only** when the loaded page is the local
 * Rafiqi origin (see WebViewEngine). It is never exposed to arbitrary websites.
 */
class RafiqiBridge(
    private val appContext: Context,
    private val scheduler: PrayerScheduler,
) {
    @JavascriptInterface
    fun getVersion(): String = "1.0"

    /**
     * Schedule (or replace) a set of prayer alarms.
     *
     * Expected JSON:
     * ```json
     * {
     *   "city": "Cairo",
     *   "prayers": [
     *     { "name": "Fajr", "label": "الفجر", "at": 1730000000000 },
     *     ...
     *   ]
     * }
     * ```
     * `at` is epoch millis (UTC). Returns a short status string for the web UI.
     */
    @JavascriptInterface
    fun schedulePrayers(json: String): String {
        return try {
            val root = JSONObject(json)
            val city = root.optString("city", "")
            val arr = root.getJSONArray("prayers")
            val list = mutableListOf<PrayerAlarm>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val label = o.optString("label", name)
                val at = o.getLong("at")
                if (at > System.currentTimeMillis() - 60_000) {
                    list += PrayerAlarm(name = name, label = label, triggerAtMillis = at)
                }
            }
            scheduler.schedule(list, city)
            "ok:${list.size}"
        } catch (e: Exception) {
            "error:${e.message ?: "parse"}"
        }
    }

    @JavascriptInterface
    fun cancelAllPrayers(): String {
        scheduler.cancelAll()
        return "ok"
    }

    @JavascriptInterface
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JavascriptInterface
    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExact()

    /**
     * Returns a JSON array of currently scheduled prayer names (for UI status).
     */
    @JavascriptInterface
    fun listScheduled(): String {
        val names = scheduler.listScheduledNames()
        val arr = JSONArray()
        names.forEach { arr.put(it) }
        return arr.toString()
    }
}

data class PrayerAlarm(
    val name: String,
    val label: String,
    val triggerAtMillis: Long,
)
