package com.opoojkk.podium.data.rss

/**
 * JVM/Desktop implementation of RssParser.
 * Uses RustRssParser with fallback to SimpleRssParser.
 */
actual fun createDefaultRssParser(): RssParser {
    return HybridRssParser()
}

/**
 * Hybrid parser that tries Rust-based parser first, falls back to SimpleRssParser on failure.
 */
private class HybridRssParser : RssParser {
    private val rustParser = RustRssParser
    private val simpleParser = SimpleRssParser()

    override fun parse(feedUrl: String, xml: String): PodcastFeed {
        // Try Rust parser first for better performance
        val rustResult = try {
            rustParser.parse(feedUrl, xml)
        } catch (e: Exception) {
            // Rust parser threw an exception, use fallback
            null
        }

        // If Rust parser succeeded, use its result
        if (rustResult != null) {
            println("✓ RSS parsed with Rust parser (high-performance)")
            return rustResult
        }

        // Fall back to SimpleRssParser for maximum compatibility
        println("⟲ Falling back to Kotlin parser for this feed")
        return simpleParser.parse(feedUrl, xml)
    }
}
