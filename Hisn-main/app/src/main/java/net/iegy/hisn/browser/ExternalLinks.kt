package net.iegy.hisn.browser

import android.content.Intent
import android.net.Uri

/**
 * Turns a link that web content (or the address bar) wants to hand to another app into a safe Intent.
 * The caller shows a confirmation first; nothing here launches anything by itself.
 */
object ExternalLinks {
    fun toIntent(url: String): Intent? {
        val scheme = UrlResolver.schemeOf(url) ?: return null
        return when (scheme) {
            // DIAL only opens the dialler with the number filled in; it never places a call.
            "tel" -> Intent(Intent.ACTION_DIAL, Uri.parse(url))
            "mailto", "sms", "smsto", "geo", "market" ->
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            "intent" -> parseIntentUri(url)
            else -> null
        }
    }

    private fun parseIntentUri(url: String): Intent? {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            return null
        }
        // Standard hardening for intent: URIs coming from web content: it may only target apps through the
        // normal implicit-intent resolver, must be BROWSABLE, and cannot carry URI-permission grants.
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = intent.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
        return intent
    }
}
