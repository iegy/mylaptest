package net.iegy.hisn.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadTest {
    @Test fun platformsAreDetected() {
        assertEquals(MediaPlatform.YOUTUBE, MediaDownload.platformOf("https://www.youtube.com/watch?v=abc"))
        assertEquals(MediaPlatform.YOUTUBE, MediaDownload.platformOf("https://youtu.be/abc"))
        assertEquals(MediaPlatform.TIKTOK, MediaDownload.platformOf("https://www.tiktok.com/@u/video/1"))
        assertEquals(MediaPlatform.INSTAGRAM, MediaDownload.platformOf("https://www.instagram.com/p/xyz/"))
        assertEquals(MediaPlatform.FACEBOOK, MediaDownload.platformOf("https://www.facebook.com/watch/?v=1"))
        assertEquals(MediaPlatform.TWITTER, MediaDownload.platformOf("https://x.com/user/status/1"))
        assertEquals(MediaPlatform.GENERIC, MediaDownload.platformOf("https://example.com/video"))
    }

    @Test fun downloadableMediaUrls() {
        assertTrue(MediaDownload.isDownloadableMediaUrl("https://cdn.example.com/clip.mp4"))
        assertTrue(MediaDownload.isDownloadableMediaUrl("https://cdn.example.com/a.webm?token=1"))
        assertTrue(MediaDownload.isDownloadableMediaUrl("https://cdn.example.com/song.mp3"))
        assertFalse(MediaDownload.isDownloadableMediaUrl("https://example.com/watch?v=1"))
        assertFalse(MediaDownload.isDownloadableMediaUrl("blob:https://example.com/uuid"))
        assertFalse(MediaDownload.isDownloadableMediaUrl("data:video/mp4;base64,aaa"))
    }

    @Test fun jsResultIsParsed() {
        assertTrue(MediaDownload.parseJsResult(null).isEmpty())
        assertTrue(MediaDownload.parseJsResult("null").isEmpty())
        assertTrue(MediaDownload.parseJsResult("").isEmpty())
        assertTrue(MediaDownload.parseJsResult("[]").isEmpty())

        val raw =
            "[{\"url\":\"https://cdn.example.com/v.mp4\",\"kind\":\"video\",\"label\":\"video\"}]"
        val list = MediaDownload.parseJsResult(raw)
        assertTrue("expected to find mp4 in $list", list.any { it.url.contains("v.mp4") })
    }

    @Test fun mergePrefersProgressiveFiles() {
        val merged = MediaDownload.mergeCandidates(
            fromPage = emptyList(),
            observedUrls = listOf(
                "https://cdn.example.com/segment-01.m4s",
                "https://cdn.example.com/full.mp4",
            ),
            pageUrl = "https://example.com/watch",
        )
        assertTrue(merged.any { it.url.endsWith(".mp4") })
    }
}
