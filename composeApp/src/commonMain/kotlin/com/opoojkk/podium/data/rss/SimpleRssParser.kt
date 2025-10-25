package com.opoojkk.podium.data.rss

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
        val artworkUrl = extractImage(channelBlock) ?: extractItunesImage(channelBlock)
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
            id = generateUniquePodcastId(title, feedUrl),
            title = title,
            description = description,
            artworkUrl = artworkUrl,
            feedUrl = feedUrl,
            lastUpdated = lastUpdated,
            episodes = items,
        )
    }

    private fun extractTag(block: String, tag: String): String? {
        val pattern = Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        val cdata = cdataRegex.find(raw)?.groupValues?.get(1)
        return (cdata ?: raw).trim()
    }

    private fun extractAttribute(block: String, tag: String, attribute: String): String? {
        val pattern = Regex("<$tag[^>]*\\b$attribute\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*/?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
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
            val trimmed = raw.trim()
            // Try ISO-8601 format first (Instant.parse handles this)
            runCatching { Instant.parse(trimmed) }.getOrElse {
                // Fall back to RFC-2822 format parsing
                runCatching { parseRfc2822Date(trimmed) }.getOrElse { Clock.System.now() }
            }
        }

    private fun parseRfc2822Date(dateString: String): Instant {
        // Common RFC-2822 patterns in RSS feeds
        val patterns = listOf(
            // "Wed, 15 Nov 2023 10:30:00 GMT"
            Regex("""\w{3},\s+(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+(\w{3})"""),
            // "Wed, 15 Nov 2023 10:30:00 +0000"
            Regex("""\w{3},\s+(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+([+-]\d{4})"""),
            // "15 Nov 2023 10:30:00 GMT" (without day of week)
            Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+(\w{3})"""),
            // "15 Nov 2023 10:30:00 +0000" (without day of week)
            Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+([+-]\d{4})""")
        )
        
        val monthMap = mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
        )
        
        for (pattern in patterns) {
            val match = pattern.find(dateString)
            if (match != null) {
                val groups = match.groupValues
                val day = groups[1].toInt()
                val monthName = groups[2]
                val year = groups[3].toInt()
                val hour = groups[4].toInt()
                val minute = groups[5].toInt()
                val second = groups[6].toInt()
                val timezone = groups[7]
                
                val month = monthMap[monthName] ?: throw IllegalArgumentException("Invalid month: $monthName")
                
                // Create LocalDateTime from parsed components
                val localDateTime = LocalDateTime(year, month, day, hour, minute, second)
                
                // Handle timezone
                val timeZone = when (timezone) {
                    "GMT", "UTC", "+0000" -> TimeZone.UTC
                    else -> {
                        // Handle timezone offsets like +0000, -0500
                        if (timezone.matches(Regex("[+-]\\d{4}"))) {
                            val sign = if (timezone.startsWith("+")) 1 else -1
                            val hours = timezone.substring(1, 3).toInt()
                            val minutes = timezone.substring(3, 5).toInt()
                            val offsetMinutes = sign * (hours * 60 + minutes)
                            // Create a timezone with the offset (in minutes)
                            val offsetHours = offsetMinutes / 60
                            val offsetMinutesRemainder = kotlin.math.abs(offsetMinutes % 60)
                            val timezoneId = "UTC${if (offsetHours >= 0) "+" else ""}${offsetHours.toString().padStart(2, '0')}:${offsetMinutesRemainder.toString().padStart(2, '0')}"
                            TimeZone.of(timezoneId)
                        } else {
                            // Default to UTC for unknown timezones
                            TimeZone.UTC
                        }
                    }
                }
                
                return localDateTime.toInstant(timeZone)
            }
        }
        
        throw IllegalArgumentException("Unable to parse date: $dateString")
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

    private fun generateUniquePodcastId(title: String, feedUrl: String): String {
        // Create a more unique ID by combining title, feedUrl, and current timestamp
        val titleHash = title.lowercase()
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        val urlHash = feedUrl.hashCode().toString().replace("-", "n")
        val timestamp = Clock.System.now().toEpochMilliseconds().toString()
        // Use a random number instead of System.nanoTime() for multiplatform compatibility
        val randomSuffix = (100000..999999).random().toString()
        return "${titleHash}-${urlHash}-${timestamp}-${randomSuffix}"
    }

    companion object {
        private val channelRegex = Regex("<channel>(.*?)</channel>", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val itemRegex = Regex("<item>(.*?)</item>", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val cdataRegex = Regex("<!\\[CDATA\\[(.*?)\\]\\]>", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
    }
}
