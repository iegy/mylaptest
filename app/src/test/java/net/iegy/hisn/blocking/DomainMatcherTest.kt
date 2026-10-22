package net.iegy.hisn.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    private val matcher = DomainMatcher.parse(
        sequenceOf(
            "# comment", "127.0.0.1 localhost", "0.0.0.0 ads.example.com", "0.0.0.0 tracker.net # trailing",
            "plain-domain.org", "bad line here", "0.0.0.0 0.0.0.0", "10.0.0.1 notsink.com",
        ),
    )

    @Test fun parsesHostsAndPlainFormatsAndSkipsJunk() = assertEquals(3, matcher.size)
    @Test fun exactAndSubdomainMatch() {
        assertTrue(matcher.isBlocked("ads.example.com"))
        assertTrue(matcher.isBlocked("x.y.tracker.net"))
    }
    @Test fun parentOfBlockedHostIsNotBlocked() = assertFalse(matcher.isBlocked("example.com"))
    @Test fun bareTldIsNeverMatched() = assertFalse(matcher.isBlocked("net"))
    @Test fun matchingIsCaseInsensitiveAndIgnoresTrailingDot() = assertTrue(matcher.isBlocked("ADS.Example.COM."))
    @Test fun localhostAndNonSinkholeAddressesAreIgnored() {
        assertFalse(matcher.isBlocked("localhost"))
        assertFalse(matcher.isBlocked("notsink.com"))
    }

    @Test fun allowlistCoversSubdomainsButNotLookalikes() {
        assertTrue(Allowlist.isAllowed(setOf("example.com"), "example.com"))
        assertTrue(Allowlist.isAllowed(setOf("example.com"), "m.example.com"))
        assertTrue(Allowlist.isAllowed(setOf("www.example.com"), "example.com"))
        assertFalse(Allowlist.isAllowed(setOf("example.com"), "badexample.com"))
        assertFalse(Allowlist.isAllowed(setOf("example.com"), null))
    }
    @Test fun firstPartyRequestsAreRecognised() {
        assertTrue(SitePolicy.isSameSite("example.com", "www.example.com"))
        assertTrue(SitePolicy.isSameSite("img.example.com", "example.com"))
        assertFalse(SitePolicy.isSameSite("tracker.net", "example.com"))
    }
}
