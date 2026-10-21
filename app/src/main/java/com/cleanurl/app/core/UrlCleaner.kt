package com.cleanurl.app.core

import java.net.URLDecoder

enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    THREADS("Threads"),
    FACEBOOK("Facebook"),
    GENERIC("通用"),
}

data class CleanOptions(
    val convertShorts: Boolean = false,
    val expandShortLinks: Boolean = true,
)

data class CleanResult(
    val original: String,
    val cleaned: String,
    val platform: Platform,
) {
    val changed: Boolean get() = original != cleaned
}

/**
 * Pure (Android-free) URL extraction and tracking-parameter removal.
 *
 * URLs are parsed by hand rather than with java.net.URI / android.net.Uri because shared links
 * often contain characters those parsers reject (e.g. `__cft__[0]`), and because untouched
 * parameters must be passed through byte-for-byte.
 */
object UrlCleaner {

    private val URL_REGEX = Regex("""https?://[^\s<>"'「」『』（）【】]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>'\"。，、；：！？…"

    private val GENERIC_TRACKING = setOf("gclid", "fbclid")
    private val GENERIC_TRACKING_PREFIXES = listOf("utm_")

    private val YOUTUBE_KEEP = setOf("v", "t", "start", "end", "list", "index", "search_query")
    // Instagram/Threads keep-lists: content URLs need no query, and Meta keeps adding new share
    // trackers (igsh, xmt, dlrf, ...), so anything not listed here is dropped.
    private val INSTAGRAM_KEEP = setOf("img_index")
    private val THREADS_KEEP = setOf("q")
    private val FACEBOOK_TRACKING = setOf(
        "mibextid", "rdid", "share_url", "ref", "refsrc", "paipv", "eav", "sfnsn",
        "_rdr", "hc_ref", "hc_location", "extid", "wtsid", "__tn__",
    )
    private val FACEBOOK_TRACKING_PREFIXES = listOf("__cft__", "__xts__", "__eep__")

    private val YOUTUBE_DOMAINS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val INSTAGRAM_DOMAINS = listOf("instagram.com")
    private val THREADS_DOMAINS = listOf("threads.net", "threads.com")
    private val FACEBOOK_DOMAINS = listOf("facebook.com", "fb.watch", "fb.com")
    private val SHORT_LINK_DOMAINS = FACEBOOK_DOMAINS + THREADS_DOMAINS + INSTAGRAM_DOMAINS

    /** Returns the first http(s) URL in [text], without trailing punctuation, or null. */
    fun extractUrl(text: String): String? {
        var url = URL_REGEX.find(text)?.value ?: return null
        while (url.isNotEmpty() && url.last() in TRAILING_PUNCTUATION) {
            // Keep a closing parenthesis that belongs to the URL, e.g. Wikipedia links.
            if (url.last() == ')' && url.count { it == '(' } >= url.count { it == ')' }) break
            url = url.dropLast(1)
        }
        return url.takeIf { it.length > "https://".length }
    }

    fun detectPlatform(url: String): Platform {
        val host = ParsedUrl.parse(url)?.host ?: return Platform.GENERIC
        return when {
            host.matchesAny(YOUTUBE_DOMAINS) -> Platform.YOUTUBE
            host.matchesAny(INSTAGRAM_DOMAINS) -> Platform.INSTAGRAM
            host.matchesAny(THREADS_DOMAINS) -> Platform.THREADS
            host.matchesAny(FACEBOOK_DOMAINS) -> Platform.FACEBOOK
            else -> Platform.GENERIC
        }
    }

    /**
     * True for Meta share links (Facebook, Threads, Instagram `/share/...`, `fb.watch`) whose real
     * target can only be found by following redirects.
     */
    fun isShortLink(url: String): Boolean {
        val parsed = ParsedUrl.parse(url) ?: return false
        if (parsed.host.matches("fb.watch")) return true
        return parsed.host.matchesAny(SHORT_LINK_DOMAINS) && parsed.path.startsWith("/share/")
    }

    /** Decoded value of the first query parameter called [name], or null. */
    fun queryParam(url: String, name: String): String? =
        ParsedUrl.parse(url)?.params
            ?.firstOrNull { it.key == name }
            ?.value
            ?.let(::decode)

    fun clean(url: String, options: CleanOptions = CleanOptions()): CleanResult {
        val platform = detectPlatform(url)
        val parsed = ParsedUrl.parse(url) ?: return CleanResult(url, url, platform)

        val cleaned = when (platform) {
            Platform.YOUTUBE -> cleanYouTube(parsed, options)
            Platform.INSTAGRAM -> parsed.removeParams { it !in INSTAGRAM_KEEP }
            Platform.THREADS -> parsed.removeParams { it !in THREADS_KEEP }
            Platform.FACEBOOK -> return cleanFacebook(url, parsed, options)
            Platform.GENERIC -> parsed.removeParams(::isGenericTracking)
        }
        return CleanResult(url, cleaned.toString(), platform)
    }

    private fun cleanYouTube(parsed: ParsedUrl, options: CleanOptions): ParsedUrl {
        val shortsId = Regex("^/shorts/([^/]+)/?$").find(parsed.path)?.groupValues?.get(1)
        if (options.convertShorts && shortsId != null) {
            val time = parsed.params.filter { it.key == "t" || it.key == "start" }
            return ParsedUrl(
                prefix = "https://www.youtube.com",
                host = "www.youtube.com",
                path = "/watch",
                params = listOf(QueryParam("v", "v=$shortsId", shortsId)) + time,
                fragment = parsed.fragment,
            )
        }
        return parsed.removeParams { it !in YOUTUBE_KEEP }
    }

    private fun cleanFacebook(url: String, parsed: ParsedUrl, options: CleanOptions): CleanResult {
        // l.facebook.com/l.php?u=<target> wraps outbound links; clean the real target instead.
        if (parsed.host.startsWith("l.") || parsed.host.startsWith("lm.")) {
            val target = queryParam(url, "u")
            if (target != null && target.startsWith("http", ignoreCase = true)) {
                return clean(target, options).copy(original = url)
            }
        }
        val cleaned = parsed.removeParams { key ->
            isGenericTracking(key) ||
                key in FACEBOOK_TRACKING ||
                FACEBOOK_TRACKING_PREFIXES.any { key.startsWith(it) }
        }
        return CleanResult(url, cleaned.toString(), Platform.FACEBOOK)
    }

    private fun isGenericTracking(key: String): Boolean =
        key in GENERIC_TRACKING || GENERIC_TRACKING_PREFIXES.any { key.startsWith(it) }

    private fun String.matches(domain: String) = this == domain || endsWith(".$domain")

    private fun String.matchesAny(domains: List<String>) = domains.any { matches(it) }

    internal fun decode(value: String): String =
        try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            value
        }

    /** A raw query parameter. [key] is decoded and lower-cased for matching; [raw] is emitted untouched. */
    internal data class QueryParam(val key: String, val raw: String, val value: String?)

    internal data class ParsedUrl(
        /** Everything before the path, e.g. `https://www.example.com:8080`. */
        val prefix: String,
        val host: String,
        val path: String,
        val params: List<QueryParam>,
        val fragment: String?,
    ) {
        fun removeParams(shouldRemove: (String) -> Boolean) =
            copy(params = params.filterNot { shouldRemove(it.key) })

        override fun toString(): String = buildString {
            append(prefix).append(path)
            if (params.isNotEmpty()) append('?').append(params.joinToString("&") { it.raw })
            if (fragment != null) append('#').append(fragment)
        }

        companion object {
            fun parse(url: String): ParsedUrl? {
                val schemeEnd = url.indexOf("://")
                if (schemeEnd <= 0) return null

                val hashIdx = url.indexOf('#')
                val fragment = if (hashIdx >= 0) url.substring(hashIdx + 1) else null
                val beforeFragment = if (hashIdx >= 0) url.substring(0, hashIdx) else url

                val queryIdx = beforeFragment.indexOf('?')
                val query = if (queryIdx >= 0) beforeFragment.substring(queryIdx + 1) else ""
                val base = if (queryIdx >= 0) beforeFragment.substring(0, queryIdx) else beforeFragment

                val authorityStart = schemeEnd + 3
                val pathIdx = base.indexOf('/', authorityStart).let { if (it < 0) base.length else it }
                val authority = base.substring(authorityStart, pathIdx)
                val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
                if (host.isEmpty()) return null

                val params = query.split('&').filter { it.isNotEmpty() }.map { raw ->
                    val eq = raw.indexOf('=')
                    val key = if (eq >= 0) raw.substring(0, eq) else raw
                    val value = if (eq >= 0) raw.substring(eq + 1) else null
                    QueryParam(decode(key).lowercase(), raw, value)
                }

                return ParsedUrl(
                    prefix = base.substring(0, pathIdx),
                    host = host,
                    path = base.substring(pathIdx),
                    params = params,
                    fragment = fragment,
                )
            }
        }
    }
}
