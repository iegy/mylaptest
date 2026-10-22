package net.iegy.hisn.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.iegy.hisn.MainActivity
import net.iegy.hisn.R

/** Pins a page to the launcher's home screen; tapping it opens the page in Hisn. */
object HomeShortcut {
    /** Returns false when the launcher cannot pin shortcuts. */
    fun request(context: Context, title: String, url: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(context, MainActivity::class.java)
        val label = title.ifBlank { UrlResolver.displayHost(url) ?: url }.take(25)
        val info = ShortcutInfoCompat.Builder(context, "page-" + url.hashCode())
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(open)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
