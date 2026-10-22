package net.iegy.hisn.browser

/** Single place that decides whether a page visit may be written to browsing history. */
object HistoryPolicy {
    fun shouldRecord(isPrivate: Boolean, url: String, hasError: Boolean): Boolean =
        !isPrivate && !hasError && UrlResolver.isWebUrl(url)
}

/** Builds the user-agent string for "desktop site" mode from the device's default mobile one. */
object UserAgents {
    private val ANDROID_PLATFORM = Regex("\\(Linux;[^)]*\\)")

    fun desktop(mobileUa: String): String =
        mobileUa
            .replace(ANDROID_PLATFORM, "(X11; Linux x86_64)")
            .replace(" Mobile", "")
}
