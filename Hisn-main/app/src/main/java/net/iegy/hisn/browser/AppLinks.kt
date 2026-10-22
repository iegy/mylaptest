package net.iegy.hisn.browser

/** Addresses the app itself points to. */
object AppLinks {
    /** The user's own QR toolkit (qrmo). It scans with the camera through the normal web camera permission prompt. */
    const val QR_TOOL = "https://qr.iegy.net/"

    /** Translates an address through Google Translate. [targetLanguage] is a language code such as "ar" or "en". */
    fun translate(url: String, targetLanguage: String): String =
        "https://translate.google.com/translate?sl=auto&tl=${android.net.Uri.encode(targetLanguage)}&u=${android.net.Uri.encode(url)}"
}
