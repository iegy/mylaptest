package net.iegy.hisn.rafiqi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.iegy.hisn.MainActivity
import net.iegy.hisn.R

/**
 * Fires when a scheduled prayer alarm triggers.
 * Shows a high-priority notification that opens the local Rafiqi page.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PrayerScheduler.ACTION_PRAYER) return
        val name = intent.getStringExtra(PrayerScheduler.EXTRA_NAME) ?: "Prayer"
        val label = intent.getStringExtra(PrayerScheduler.EXTRA_LABEL) ?: name
        Log.i(TAG, "Showing prayer notification: $label")

        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(RafiqiUrls.HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_prayer", true)
            putExtra("prayer_name", name)
        }
        val pi = PendingIntent.getActivity(
            context,
            name.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // replace with app icon later
            .setContentTitle(context.getString(R.string.rafiqi_prayer_title, label))
            .setContentText(context.getString(R.string.rafiqi_prayer_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(name.hashCode() and 0x7FFF, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing", e)
        }

        // Inform scheduler (optional cleanup)
        PrayerScheduler(context.applicationContext).onAlarmFired(name)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.rafiqi_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.rafiqi_channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "PrayerAlarmReceiver"
        const val CHANNEL_ID = "hisn_prayer"
    }
}
