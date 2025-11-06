package com.opoojkk.podium.data.rss

import com.opoojkk.podium.data.model.Chapter
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Rust-based RSS parser using feed-rs library.
 * This provides a high-performance alternative to the SimpleRssParser.
 */
object RustRssParser {

    init {
        try {
            System.loadLibrary("rust_rss_parser")
        } catch (e: UnsatisfiedLinkError) {
            println("Warning: Failed to load native library rust_rss_parser: ${e.message}")
            println("Falling back to SimpleRssParser if needed")
        }
    }

    /**
     * Parse RSS feed using the native Rust implementation.
     *
     * @param feedUrl The URL of the RSS feed
     * @param xmlContent The XML content of the feed
     * @return Parsed PodcastFeed or null if parsing failed
     */
    fun parse(feedUrl: String, xmlContent: String): PodcastFeed? {
        return try {
            val jsonResult = parseRss(feedUrl, xmlContent)
            val result = Json.decodeFromString<RustPodcastFeed>(jsonResult)

            // Check for error (feed-rs encountered an incompatible feed format)
            if (result.error != null) {
                // This is expected for some feed formats - will fallback to SimpleRssParser
                return null
            }

            // Convert Rust result to Kotlin PodcastFeed
            PodcastFeed(
                id = result.id ?: return null,
                title = result.title ?: return null,
                description = result.description ?: "",
                artworkUrl = result.artworkUrl,
                feedUrl = result.feedUrl ?: feedUrl,
                lastUpdated = result.lastUpdated?.let { Instant.fromEpochMilliseconds(it) }
                    ?: Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                episodes = result.episodes?.map { episode ->
                    RssEpisode(
                        id = episode.id,
                        title = episode.title,
                        description = episode.description,
                        audioUrl = episode.audioUrl,
                        publishDate = Instant.fromEpochMilliseconds(episode.publishDate),
                        duration = episode.duration,
                        imageUrl = episode.imageUrl,
                        chapters = episode.chapters.map { chapter ->
                            Chapter(
                                startTimeMs = chapter.startTimeMs,
                                title = chapter.title,
                                imageUrl = chapter.imageUrl,
                                url = chapter.url
                            )
                        }
                    )
                } ?: emptyList()
            )
        } catch (e: Exception) {
            println("Failed to parse RSS with Rust parser: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Native method implemented in Rust.
     * Returns JSON string containing the parsed feed or error.
     */
    private external fun parseRss(feedUrl: String, xmlContent: String): String
}

/**
 * Internal data structures for deserializing Rust JSON output
 */
@Serializable
private data class RustPodcastFeed(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val feedUrl: String? = null,
    val lastUpdated: Long? = null,
    val episodes: List<RustRssEpisode>? = null,
    val error: String? = null
)

@Serializable
private data class RustRssEpisode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDate: Long,
    val duration: Long? = null,
    val imageUrl: String? = null,
    val chapters: List<RustChapter> = emptyList()
)

@Serializable
private data class RustChapter(
    val startTimeMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null
)
