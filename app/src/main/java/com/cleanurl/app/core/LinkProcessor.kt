package com.cleanurl.app.core

/** Shared pipeline for the share sheet and the main screen: extract → (expand) → clean. */
class LinkProcessor(private val unshortener: UrlUnshortener = UrlUnshortener()) {

    /** Returns null when [text] contains no URL. */
    suspend fun process(text: String, options: CleanOptions): CleanResult? {
        val url = UrlCleaner.extractUrl(text) ?: return null
        val target = if (options.expandShortLinks && UrlCleaner.isShortLink(url)) {
            unshortener.expand(url) ?: url
        } else {
            url
        }
        return UrlCleaner.clean(target, options).copy(original = url)
    }
}
