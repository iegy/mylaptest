package net.iegy.hisn.browser

private val MEDIA_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "mp4", "webm", "mkv", "avi", "mp3", "wav")

/**
 * True when [url] points straight at a media file. Only the path is looked at: query and fragment are cut off first,
 * so `https://site.com/view?src=a.jpg` is a normal page, while `https://site.com/a.jpg?size=big` is an image.
 */
internal fun isDirectMediaUrl(url: String): Boolean {
    val path = url.substringBefore('#').substringBefore('?')
    val fileName = path.substringAfterLast('/')
    if (!fileName.contains('.')) return false
    return fileName.substringAfterLast('.').lowercase() in MEDIA_EXTENSIONS
}
