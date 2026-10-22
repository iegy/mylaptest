package net.iegy.hisn.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {
    @Test fun pathTraversalIsRemoved() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../etc/passwd"))
        assertEquals("win.ini", FileNameSanitizer.sanitize("..\\..\\win.ini"))
    }
    @Test fun emptyOrDotNamesFallBack() {
        assertEquals("download", FileNameSanitizer.sanitize(".."))
        assertEquals("download", FileNameSanitizer.sanitize("  "))
        assertEquals("download", FileNameSanitizer.sanitize(null))
        assertEquals("clip", FileNameSanitizer.sanitize("////", "clip"))
    }
    @Test fun illegalCharactersAreReplaced() = assertEquals("a_b_c_d.txt", FileNameSanitizer.sanitize("a<b>c:d.txt"))
    @Test fun arabicNamesAreKept() = assertEquals("تقرير 2026.pdf", FileNameSanitizer.sanitize("تقرير 2026.pdf"))
    @Test fun leadingDotsAreRemoved() = assertEquals("hidden.txt", FileNameSanitizer.sanitize(".hidden.txt"))
    @Test fun longNamesKeepTheirExtension() {
        val name = FileNameSanitizer.sanitize("a".repeat(300) + ".mp4")
        assertTrue(name.length <= 120)
        assertTrue(name.endsWith(".mp4"))
    }
    @Test fun uniqueNameAddsCounter() {
        assertEquals("a (2).txt", FileNameSanitizer.uniqueName("a.txt") { it == "a.txt" || it == "a (1).txt" })
        assertEquals("a.txt", FileNameSanitizer.uniqueName("a.txt") { false })
    }
}
