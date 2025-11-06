package com.opoojkk.podium.data.rss

import com.opoojkk.podium.data.model.Chapter
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files

/**
 * Rust-based RSS parser using feed-rs library for JVM/Desktop platform.
 * This provides a high-performance alternative to the SimpleRssParser.
 */
object RustRssParser {

    private var libraryLoaded = false

    init {
        try {
            loadNativeLibrary()
            libraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            println("Warning: Failed to load native library rust_rss_parser: ${e.message}")
            println("Falling back to SimpleRssParser if needed")
        } catch (e: Exception) {
            println("Warning: Error loading native library: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Load the native library based on the current OS and architecture.
     */
    private fun loadNativeLibrary() {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val libraryPath = when {
            osName.contains("mac") || osName.contains("darwin") -> {
                when {
                    osArch.contains("aarch64") || osArch.contains("arm") ->
                        "darwin-aarch64/librust_rss_parser.dylib"
                    else ->
                        "darwin-x86_64/librust_rss_parser.dylib"
                }
            }
            osName.contains("windows") -> {
                "windows-x86_64/rust_rss_parser.dll"
            }
            osName.contains("linux") -> {
                when {
                    osArch.contains("aarch64") || osArch.contains("arm") ->
                        "linux-aarch64/librust_rss_parser.so"
                    else ->
                        "linux-x86_64/librust_rss_parser.so"
                }
            }
            else -> throw UnsatisfiedLinkError("Unsupported OS: $osName")
        }

        // Extract library from resources to a temporary file
        val inputStream = RustRssParser::class.java.classLoader.getResourceAsStream(libraryPath)
            ?: throw UnsatisfiedLinkError("Library not found in resources: $libraryPath")

        val tempFile = Files.createTempFile("librust_rss_parser", getLibraryExtension()).toFile()
        tempFile.deleteOnExit()

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Set executable permissions on Unix-like systems
        if (!osName.contains("windows")) {
            tempFile.setExecutable(true)
            tempFile.setReadable(true)
        }

        System.load(tempFile.absolutePath)
        println("Successfully loaded native library from: ${tempFile.absolutePath}")
    }

    private fun getLibraryExtension(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> ".dylib"
            osName.contains("windows") -> ".dll"
            else -> ".so"
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
        if (!libraryLoaded) {
            println("Native library not loaded, cannot parse with Rust parser")
            return null
        }

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
