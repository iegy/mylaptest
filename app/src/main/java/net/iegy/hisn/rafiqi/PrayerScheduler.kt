package net.iegy.hisn.rafiqi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schedules exact (or inexact fallback) alarms for prayer times.
 * Uses a dedicated SharedPreferences file so the web app and the receiver
 * stay in sync without touching the main browser settings.
 */
class PrayerScheduler(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) cancelAll()
    }

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(prayers: List<PrayerAlarm>, city: String) {
        if (!isEnabled()) {
            Log.i(TAG, "Prayer notifications disabled — skip schedule")
            return
        }
        cancelAllInternal()
        if (prayers.isEmpty()) {
            prefs.edit().clear().apply()
            return
        }

        val arr = JSONArray()
        prayers.forEachIndexed { index, p ->
            val o = JSONObject()
            o.put("name", p.name)
            o.put("label", p.label)
            o.put("at", p.triggerAtMillis)
            arr.put(o)
            setAlarm(index, p)
        }
        prefs.edit()
            .putString(KEY_PRAYERS, arr.toString())
            .putString(KEY_CITY, city)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "Scheduled ${prayers.size} prayer alarms (city=$city)")
    }

    fun cancelAll() {
        cancelAllInternal()
        prefs.edit().clear().apply()
        Log.i(TAG, "All prayer alarms cancelled")
    }

    fun listScheduledNames(): List<String> {
        val raw = prefs.getString(KEY_PRAYERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Called from the receiver after an alarm fires so the next day's set can be refreshed by the web app. */
    fun onAlarmFired(name: String) {
        Log.i(TAG, "Prayer alarm fired: $name")
        // Leave the remaining future alarms; the web app should re-schedule daily when opened.
    }

    private fun cancelAllInternal() {
        val raw = prefs.getString(KEY_PRAYERS, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val pi = pendingIntentFor(i)
                alarmManager.cancel(pi)
                pi.cancel()
            }
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun setAlarm(requestCode: Int, prayer: PrayerAlarm) {
        val pi = pendingIntentFor(requestCode, prayer)
        val trigger = prayer.triggerAtMillis
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                // Fallback: inexact window still useful for soft reminder.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntentFor(requestCode: Int, prayer: PrayerAlarm? = null): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER
            putExtra(EXTRA_NAME, prayer?.name ?: "")
            putExtra(EXTRA_LABEL, prayer?.label ?: "")
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "PrayerScheduler"
        private const val PREFS = "hisn_rafiqi_prayers"
        private const val KEY_PRAYERS = "prayers"
        private const val KEY_CITY = "city"
        private const val KEY_UPDATED = "updated"
        private const val KEY_ENABLED = "enabled"

        const val ACTION_PRAYER = "net.iegy.hisn.rafiqi.PRAYER_ALARM"
        const val EXTRA_NAME = "name"
        const val EXTRA_LABEL = "label"
        const val EXTRA_REQUEST_CODE = "request_code"
    }
}
