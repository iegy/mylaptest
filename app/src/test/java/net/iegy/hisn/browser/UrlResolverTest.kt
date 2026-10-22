package net.iegy.hisn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {
    private fun resolve(s: String, engine: SearchEngine = SearchEngine.GOOGLE) = UrlResolver.resolve(s, engine)
    private fun nav(s: String) = (resolve(s) as? ResolvedInput.Navigate)?.url

    @Test fun bareDomainBecomesHttps() = assertEquals("https://example.com", nav("example.com"))
    @Test fun wwwDomainWithPathAndQuery() = assertEquals("https://www.example.com/a?b=1", nav("www.example.com/a?b=1"))
    @Test fun explicitSchemesAreKept() {
        assertEquals("https://example.com/x", nav("https://example.com/x"))
        assertEquals("http://example.com", nav("http://example.com"))
    }
    @Test fun localhostWithPortIsNotMistakenForScheme() = assertEquals("http://localhost:8080", nav("localhost:8080"))
    @Test fun ipv4IsNavigatedOverHttp() = assertEquals("http://192.168.1.1", nav("192.168.1.1"))
    @Test fun invalidIpv4IsSearched() = assertTrue(resolve("999.1.1.1") is ResolvedInput.Search)
    @Test fun idnDomainsAreRecognised() = assertEquals("https://موقع.مصر", nav("موقع.مصر"))
    @Test fun plainWordIsSearched() = assertTrue(resolve("hello") is ResolvedInput.Search)
    @Test fun sentenceWithDotIsSearched() = assertTrue(resolve("what is kotlin. really") is ResolvedInput.Search)
    @Test fun sentenceWithColonIsSearched() = assertTrue(resolve("note: buy milk") is ResolvedInput.Search)

    @Test fun arabicQueryIsPercentEncodedAsUtf8() {
        val result = resolve("اخبار اليوم") as ResolvedInput.Search
        assertTrue(result.url.startsWith("https://www.google.com/search?q=%D8%A7"))
        assertEquals("اخبار اليوم", result.query)
    }
    @Test fun searchEngineIsHonoured() {
        assertEquals("https://www.bing.com/search?q=test", (resolve("test", SearchEngine.BING) as ResolvedInput.Search).url)
        assertEquals("https://duckduckgo.com/?q=test", (resolve("test", SearchEngine.DUCKDUCKGO) as ResolvedInput.Search).url)
    }

    @Test fun dangerousSchemesAreRejected() {
        listOf("javascript:alert(1)", "JaVaScRiPt:alert(1)", "javascript:alert(1) x", "file:///etc/passwd",
            "content://x/y", "data:text/html,<b>x</b>", "vbscript:x", "blob:https://a/b").forEach {
            assertTrue("$it should be rejected", resolve(it) is ResolvedInput.Rejected)
        }
    }
    @Test fun textIsNeverTreatedAsLocalPath() {
        assertTrue(resolve("/etc/passwd") is ResolvedInput.Search)
        assertFalse(resolve("C:\\Users\\x") is ResolvedInput.Navigate)
    }
    @Test fun mailtoAndTelAreExternal() {
        assertTrue(resolve("mailto:a@b.com") is ResolvedInput.External)
        assertTrue(resolve("tel:+20100") is ResolvedInput.External)
    }
    @Test fun emptyIsRejected() = assertEquals(ResolvedInput.Rejected(RejectReason.EMPTY), resolve("   "))
    @Test fun schemeWithoutAuthorityIsInvalid() = assertTrue(resolve("http:example") is ResolvedInput.Rejected)
    @Test fun spacesInUrlAreEncoded() = assertEquals("https://example.com/a%20b", nav("https://example.com/a b"))

    @Test fun hostParsingStripsUserInfoAndPort() {
        assertEquals("example.com", UrlResolver.hostOf("https://user:pw@Example.com:8443/x"))
        assertEquals("example.com", UrlResolver.displayHost("https://www.example.com/"))
        assertNull(UrlResolver.hostOf("not a url"))
    }
    @Test fun credentialsAreStrippedFromStoredUrls() {
        assertEquals("https://example.com:8080/a?b=1#c", UrlResolver.stripUserInfo("https://user:pw@example.com:8080/a?b=1#c"))
        assertEquals("https://example.com/a@b", UrlResolver.stripUserInfo("https://example.com/a@b"))
    }
    @Test fun directMediaDetection() {
        assertTrue(UrlResolver.isDirectMedia("https://x.com/v/clip.MP4?token=1"))
        assertFalse(UrlResolver.isDirectMedia("https://x.com/index.html"))
        assertFalse(UrlResolver.isDirectMedia("javascript:void(0).mp4"))
    }
    @Test fun httpsIsDetectedAsSecure() {
        assertTrue(UrlResolver.isSecure("https://a.com"))
        assertFalse(UrlResolver.isSecure("http://a.com"))
    }
    @Test fun shortcutUrlsMustBeWeb() {
        assertEquals("https://example.org", UrlResolver.normalizeWebUrl("example.org"))
        assertNull(UrlResolver.normalizeWebUrl("javascript:1"))
    }
}
