package com.opoojkk.podium.data.rss

/**
 * JVM/Desktop implementation of RssParser.
 * Uses RustRssParser with fallback to SimpleRssParser.
 */
actual fun createDefaultRssParser(): RssParser {
    return HybridRssParser(rustParser = RustRssParser)
}
