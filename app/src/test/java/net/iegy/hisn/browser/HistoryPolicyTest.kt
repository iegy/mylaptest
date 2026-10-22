package net.iegy.hisn.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPolicyTest {
    @Test fun privateVisitsAreNeverRecorded() = assertFalse(HistoryPolicy.shouldRecord(true, "https://x.com", false))
    @Test fun regularVisitsAreRecorded() = assertTrue(HistoryPolicy.shouldRecord(false, "https://x.com", false))
    @Test fun failedLoadsAreNotRecorded() = assertFalse(HistoryPolicy.shouldRecord(false, "https://x.com", true))
    @Test fun nonWebPagesAreNotRecorded() = assertFalse(HistoryPolicy.shouldRecord(false, "about:blank", false))

    @Test fun desktopUserAgentDropsAndroidAndMobileMarkers() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        val desktop = UserAgents.desktop(mobile)
        assertTrue(desktop.contains("(X11; Linux x86_64)"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
    }
}
