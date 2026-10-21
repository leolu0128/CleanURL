package com.cleanurl.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCleanerTest {

    private fun clean(url: String, convertShorts: Boolean = false) =
        UrlCleaner.clean(url, CleanOptions(convertShorts = convertShorts)).cleaned

    // ---- extractUrl ----

    @Test
    fun extractUrl_picksUrlOutOfSharedText() {
        val text = "看看這部影片！ https://youtu.be/abc123?si=XYZ 超好笑"
        assertEquals("https://youtu.be/abc123?si=XYZ", UrlCleaner.extractUrl(text))
    }

    @Test
    fun extractUrl_trimsTrailingPunctuation() {
        assertEquals(
            "https://www.instagram.com/p/C1/",
            UrlCleaner.extractUrl("連結：https://www.instagram.com/p/C1/。"),
        )
        assertEquals(
            "https://example.com/a",
            UrlCleaner.extractUrl("(see https://example.com/a)"),
        )
    }

    @Test
    fun extractUrl_returnsNullWithoutUrl() {
        assertNull(UrlCleaner.extractUrl("沒有網址的文字"))
    }

    // ---- YouTube ----

    @Test
    fun youtube_removesTrackingKeepsVideoAndTime() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42",
            clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc&feature=share&t=42&pp=ygU"),
        )
    }

    @Test
    fun youtube_shortLinkDropsSi() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", clean("https://youtu.be/dQw4w9WgXcQ?si=Ab12Cd"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=10", clean("https://youtu.be/dQw4w9WgXcQ?si=Ab12Cd&t=10"))
    }

    @Test
    fun youtube_keepsPlaylistParams() {
        assertEquals(
            "https://m.youtube.com/watch?v=abc&list=PL123&index=3",
            clean("https://m.youtube.com/watch?v=abc&list=PL123&index=3&kw=x&si=y"),
        )
    }

    @Test
    fun youtube_shortsKeptWhenConversionDisabled() {
        assertEquals("https://youtube.com/shorts/xyz789", clean("https://youtube.com/shorts/xyz789?si=abc"))
    }

    @Test
    fun youtube_shortsConvertedWhenEnabled() {
        assertEquals(
            "https://www.youtube.com/watch?v=xyz789",
            clean("https://youtube.com/shorts/xyz789?si=abc&feature=share", convertShorts = true),
        )
    }

    @Test
    fun youtube_detectsPlatform() {
        assertEquals(Platform.YOUTUBE, UrlCleaner.clean("https://youtu.be/a").platform)
        assertEquals(Platform.YOUTUBE, UrlCleaner.clean("https://m.youtube.com/watch?v=a").platform)
    }

    // ---- Instagram ----

    @Test
    fun instagram_removesIgsh() {
        assertEquals(
            "https://www.instagram.com/reel/C9abc/",
            clean("https://www.instagram.com/reel/C9abc/?igsh=MWQ1ZGUxMzBkMA=="),
        )
        assertEquals(
            "https://www.instagram.com/p/C9abc/",
            clean("https://www.instagram.com/p/C9abc/?igshid=abc&utm_source=ig_web_copy_link"),
        )
    }

    @Test
    fun instagram_keepsUnknownParams() {
        assertEquals(
            "https://www.instagram.com/p/C9abc/?img_index=2",
            clean("https://www.instagram.com/p/C9abc/?img_index=2&igsh=xyz"),
        )
    }

    // ---- Threads ----

    @Test
    fun threads_removesXmt() {
        assertEquals(
            "https://www.threads.net/@user/post/C9abc",
            clean("https://www.threads.net/@user/post/C9abc?xmt=AQGz&slof=1"),
        )
        assertEquals(
            "https://www.threads.com/@user/post/C9abc",
            clean("https://www.threads.com/@user/post/C9abc?xmt=AQGz&igshid=1"),
        )
        assertEquals(Platform.THREADS, UrlCleaner.clean("https://www.threads.com/@a").platform)
    }

    // ---- Facebook ----

    @Test
    fun facebook_removesTrackingKeepsIds() {
        assertEquals(
            "https://www.facebook.com/permalink.php?story_fbid=123&id=456",
            clean("https://www.facebook.com/permalink.php?story_fbid=123&id=456&mibextid=abc&rdid=XYZ&__cft__[0]=AZW&__tn__=%2CO%2CP-R"),
        )
        assertEquals(
            "https://m.facebook.com/watch/?v=789",
            clean("https://m.facebook.com/watch/?v=789&ref=sharing&fbclid=IwAR0"),
        )
    }

    @Test
    fun facebook_removesEncodedCftKey() {
        assertEquals(
            "https://www.facebook.com/user/posts/pfbid0abc",
            clean("https://www.facebook.com/user/posts/pfbid0abc?__cft__%5B0%5D=AZW&__tn__=%2CO"),
        )
    }

    @Test
    fun facebook_detectsShortLinks() {
        assertTrue(UrlCleaner.isFacebookShortLink("https://www.facebook.com/share/p/1AbCdEf/"))
        assertTrue(UrlCleaner.isFacebookShortLink("https://www.facebook.com/share/r/1AbCdEf/"))
        assertTrue(UrlCleaner.isFacebookShortLink("https://m.facebook.com/share/v/1AbCdEf/"))
        assertTrue(UrlCleaner.isFacebookShortLink("https://www.facebook.com/share/1AbCdEf/"))
        assertTrue(UrlCleaner.isFacebookShortLink("https://fb.watch/abcDEF/"))
        assertFalse(UrlCleaner.isFacebookShortLink("https://www.facebook.com/user/posts/123"))
        assertFalse(UrlCleaner.isFacebookShortLink("https://www.youtube.com/share/x"))
    }

    @Test
    fun facebook_unwrapsOutboundRedirect() {
        assertEquals(
            "https://example.com/article",
            clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle%3Futm_source%3Dfb%26fbclid%3Dx&h=AT0"),
        )
    }

    @Test
    fun facebook_fbComAliasDetected() {
        assertEquals(Platform.FACEBOOK, UrlCleaner.clean("https://fb.com/123").platform)
        assertEquals(Platform.FACEBOOK, UrlCleaner.clean("https://fb.watch/abc/").platform)
    }

    // ---- Generic ----

    @Test
    fun generic_removesUtmAndClickIds() {
        assertEquals(
            "https://news.example.com/a?id=5#top",
            clean("https://news.example.com/a?utm_source=x&id=5&utm_medium=y&gclid=z&fbclid=w&utm_campaign=c&utm_term=t&utm_content=q#top"),
        )
        assertEquals(Platform.GENERIC, UrlCleaner.clean("https://news.example.com/a").platform)
    }

    @Test
    fun generic_untouchedUrlUnchanged() {
        val url = "https://example.com/path?q=hello%20world&page=2"
        val result = UrlCleaner.clean(url)
        assertEquals(url, result.cleaned)
        assertFalse(result.changed)
    }

    @Test
    fun hostMatchingDoesNotMatchLookalikes() {
        assertEquals(Platform.GENERIC, UrlCleaner.clean("https://notyoutube.com/watch?v=a&si=b").platform)
    }

    // ---- helpers ----

    @Test
    fun queryParam_decodesValue() {
        assertEquals(
            "https://www.facebook.com/x/posts/1",
            UrlCleaner.queryParam("https://www.facebook.com/login/?next=https%3A%2F%2Fwww.facebook.com%2Fx%2Fposts%2F1", "next"),
        )
    }
}
