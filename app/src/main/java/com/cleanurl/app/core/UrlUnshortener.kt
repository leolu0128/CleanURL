package com.cleanurl.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Expands Facebook share short links (`facebook.com/share/...`, `fb.watch/...`) by following
 * HTTP redirects by hand. No cookies are sent (no CookieHandler is installed), and any failure
 * or timeout yields null so callers can fall back to the original link.
 */
class UrlUnshortener(private val timeoutMs: Long = 3_000) {

    suspend fun expand(url: String): String? {
        // async + await rather than withContext so that a stuck socket can't hold up the caller
        // past the timeout: the abandoned request finishes (or times out) on its own.
        val request = scope.async { follow(url) }
        return withTimeoutOrNull(timeoutMs) { request.await() }
            ?: run { request.cancel(); null }
    }

    private fun follow(start: String): String? {
        var current = start
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    val next = URL(URL(current), location).toString()
                    unwrapLoginRedirect(next)?.let { return it }
                    if (!UrlCleaner.isFacebookShortLink(next)) return next
                    current = next
                } else if (code == HttpURLConnection.HTTP_OK) {
                    return readCanonicalUrl(connection)
                } else {
                    return null
                }
            } catch (e: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    /** Logged-out requests are often bounced to `/login/?next=<real post>`. */
    private fun unwrapLoginRedirect(url: String): String? {
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?')
        if (!path.startsWith("login") && !path.startsWith("checkpoint")) return null
        return UrlCleaner.queryParam(url, "next")?.takeIf { it.startsWith("http") }
    }

    /** Some share links answer 200 with the real URL only in `og:url` / canonical. */
    private fun readCanonicalUrl(connection: HttpURLConnection): String? {
        val html = connection.inputStream.use { stream ->
            val buffer = ByteArray(MAX_HTML_BYTES)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            String(buffer, 0, total, Charsets.UTF_8)
        }
        val match = OG_URL.find(html) ?: CANONICAL.find(html) ?: return null
        return match.groupValues[1]
            .replace("&amp;", "&")
            .takeIf { it.startsWith("http") && !UrlCleaner.isFacebookShortLink(it) }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_HTML_BYTES = 512 * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
        val OG_URL = Regex("""<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']""")
        val CANONICAL = Regex("""<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
