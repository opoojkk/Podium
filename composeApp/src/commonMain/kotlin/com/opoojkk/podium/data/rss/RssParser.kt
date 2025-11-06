package com.opoojkk.podium.data.rss

/**
 * Interface for RSS feed parsers.
 * Allows for different implementations (e.g., Kotlin-based, Rust-based).
 */
interface RssParser {
    /**
     * Parse an RSS/Atom feed from XML content.
     *
     * @param feedUrl The URL of the feed
     * @param xml The XML content of the feed
     * @return Parsed PodcastFeed
     */
    fun parse(feedUrl: String, xml: String): PodcastFeed
}

/**
 * Platform-specific function to create the default RSS parser.
 * - Android: Uses RustRssParser with fallback to SimpleRssParser
 * - JVM/Desktop: Uses RustRssParser with fallback to SimpleRssParser
 * - iOS: Uses SimpleRssParser (Rust parser requires Xcode setup)
 */
expect fun createDefaultRssParser(): RssParser
