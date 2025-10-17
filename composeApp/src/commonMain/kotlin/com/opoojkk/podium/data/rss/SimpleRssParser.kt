package com.opoojkk.podium.data.rss

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A lightweight RSS parser that extracts the minimum amount of information required for the UI.
 * It is intentionally lenient and falls back to sensible defaults when encountering unexpected
 * feed formats so that the app can keep functioning while providing room for platform-specific
 * improvements later on.
 */
class SimpleRssParser {

    fun parse(feedUrl: String, xml: String): PodcastFeed {
        val channelBlock = channelRegex.find(xml)?.groupValues?.get(1) ?: xml
        val title = extractTag(channelBlock, "title") ?: feedUrl
        val description = extractTag(channelBlock, "description") ?: ""
        val artworkUrl = extractImage(channelBlock)
        val items = itemRegex.findAll(channelBlock).map { match ->
            val itemBlock = match.groupValues[1]
            val guid = extractTag(itemBlock, "guid") ?: extractTag(itemBlock, "id") ?: extractTag(itemBlock, "link") ?: itemBlock.hashCode().toString()
            val itemTitle = extractTag(itemBlock, "title") ?: guid
            val itemDescription = extractTag(itemBlock, "description") ?: ""
            val enclosureUrl = extractAttribute(itemBlock, "enclosure", "url")
                ?: extractTag(itemBlock, "link")
                ?: ""
            val publishDate = parseDate(extractTag(itemBlock, "pubDate") ?: extractTag(itemBlock, "published"))
            val duration = parseDuration(extractTag(itemBlock, "itunes:duration"))
            val episodeImage = extractImage(itemBlock) ?: extractItunesImage(itemBlock)
            RssEpisode(
                id = guid,
                title = itemTitle,
                description = itemDescription,
                audioUrl = enclosureUrl,
                publishDate = publishDate,
                duration = duration,
                imageUrl = episodeImage,
            )
        }.filter { it.audioUrl.isNotBlank() }
            .toList()

        val lastUpdated = items.firstOrNull()?.publishDate ?: Clock.System.now()
        return PodcastFeed(
            id = title.lowercase().replace(" ", "-") + feedUrl.hashCode(),
            title = title,
            description = description,
            artworkUrl = artworkUrl,
            feedUrl = feedUrl,
            lastUpdated = lastUpdated,
            episodes = items,
        )
    }

    private fun extractTag(block: String, tag: String): String? {
        val pattern = Regex("<$tag(?: [^>]*)?>(.*?)</$tag>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        val cdata = cdataRegex.find(raw)?.groupValues?.get(1)
        return (cdata ?: raw).trim()
    }

    private fun extractAttribute(block: String, tag: String, attribute: String): String? {
        val pattern = Regex("<$tag[^>]*$attribute=\"([^\"]+)\"[^>]*/?>", RegexOption.IGNORE_CASE)
        return pattern.find(block)?.groupValues?.get(1)
    }

    private fun extractImage(block: String): String? =
        extractTag(block, "image")?.let { imageBlock ->
            extractTag(imageBlock, "url") ?: extractAttribute(imageBlock, "image", "href")
        }

    private fun extractItunesImage(block: String): String? =
        extractAttribute(block, "itunes:image", "href")

    private fun parseDate(raw: String?): Instant =
        if (raw == null) {
            Clock.System.now()
        } else {
            runCatching { Instant.parse(raw.trim()) }.getOrElse { Clock.System.now() }
        }

    private fun parseDuration(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                else -> raw.toLong() * 1000
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    companion object {
        private val channelRegex = Regex("<channel>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)
        private val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)
        private val cdataRegex = Regex("<!\[CDATA\[(.*?)]\]>", RegexOption.DOT_MATCHES_ALL)
    }
}
