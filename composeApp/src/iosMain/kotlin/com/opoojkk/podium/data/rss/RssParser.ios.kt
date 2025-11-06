package com.opoojkk.podium.data.rss

/**
 * iOS implementation of RssParser.
 * Uses SimpleRssParser (Rust parser requires Xcode setup).
 */
actual fun createDefaultRssParser(): RssParser {
    return SimpleRssParser()
}
