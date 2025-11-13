package com.opoojkk.podium.data.rss

/**
 * Hybrid RSS parser.
 * Tries Rust parser first (high performance), falls back to Kotlin parser (maximum compatibility).
 *
 * @param rustParser Rust-based parser
 * @param kotlinParser Kotlin-based parser (fallback)
 */
internal class HybridRssParser(
    private val rustParser: RssParser,
    private val kotlinParser: RssParser = SimpleRssParser()
) : RssParser {

    override fun parse(feedUrl: String, xml: String): PodcastFeed {
        // Try Rust parser first for better performance
        val rustResult = try {
            rustParser.parse(feedUrl, xml)
        } catch (e: Exception) {
            // Rust parser threw exception, use fallback
            null
        }

        // If Rust parser succeeded, use its result
        if (rustResult != null) {
            println("✓ RSS parsed with Rust parser (high-performance)")
            return rustResult
        }

        // Fall back to Kotlin parser for maximum compatibility
        println("⟲ Falling back to Kotlin parser for this feed")
        return kotlinParser.parse(feedUrl, xml)
    }
}
