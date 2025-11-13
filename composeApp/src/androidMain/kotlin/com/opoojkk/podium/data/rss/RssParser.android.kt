package com.opoojkk.podium.data.rss

/**
 * Android implementation of RssParser.
 * Uses RustRssParser with fallback to SimpleRssParser.
 */
actual fun createDefaultRssParser(): RssParser {
    return HybridRssParser(rustParser = RustRssParserAdapter)
}

/**
 * Adapter to make RustRssParser compatible with the RssParser interface.
 */
private object RustRssParserAdapter : RssParser {
    override fun parse(feedUrl: String, xml: String): PodcastFeed {
        return RustRssParser.parse(feedUrl, xml)
            ?: throw IllegalStateException("Rust parser returned null")
    }
}
