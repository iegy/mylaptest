package net.iegy.hisn.browser

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlsTest {
    @Test fun directMediaFilesAreRecognised() {
        assertTrue(isDirectMediaUrl("https://site.com/photos/a.jpg"))
        assertTrue(isDirectMediaUrl("https://site.com/clip.MP4"))
        assertTrue(isDirectMediaUrl("https://site.com/a.png?size=big#top"))
    }

    @Test fun normalPagesWithMediaInTheQueryAreNotMedia() {
        assertFalse(isDirectMediaUrl("https://site.com/view?src=a.jpg"))
        assertFalse(isDirectMediaUrl("https://site.com/page#photo.png"))
        assertFalse(isDirectMediaUrl("https://site.com/article/2026"))
        assertFalse(isDirectMediaUrl("https://site.com/"))
        assertFalse(isDirectMediaUrl("https://site.com/index.html"))
    }

    @Test fun screenshotFileNamesAreSafe() {
        val now = Date(0)
        val plain = ScreenshotSaver.fileName("", now)
        assertTrue(plain.startsWith("Hisn_") && plain.endsWith(".png"))
        val named = ScreenshotSaver.fileName("  مرحبا: بالعالم / test?  ", now)
        assertTrue(named.endsWith("_مرحبا_بالعالم_test.png"))
        assertFalse(named.contains('/') || named.contains(':') || named.contains('?'))
        assertEquals(40, ScreenshotSaver.fileName("a".repeat(100), now).substringAfter("_", "").substringAfter("_").substringAfter("_").removeSuffix(".png").length)
    }
}
