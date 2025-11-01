package com.opoojkk.podium.data.rss

import com.opoojkk.podium.data.model.Chapter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs

/**
 * A lightweight RSS parser that extracts the information required for the UI while remaining
 * resilient against the many variants of podcast feeds found in the wild.
 */
class SimpleRssParser {

    fun parse(feedUrl: String, xml: String): PodcastFeed {
        val sanitizedXml = xml.removePrefix("\uFEFF")
        val channelBlock = channelRegex.find(sanitizedXml)?.groupValues?.get(1) ?: sanitizedXml

        val title = firstNonBlank(
            extractTag(channelBlock, "title"),
            extractTag(channelBlock, "itunes:title"),
        )?.let(::sanitizeInlineText) ?: feedUrl

        val description = firstNonBlank(
            extractTag(channelBlock, "itunes:summary"),
            extractTag(channelBlock, "description"),
            extractTag(channelBlock, "subtitle"),
        )?.let(::sanitizeRichText) ?: ""

        val artworkUrl = firstNonBlank(
            extractItunesImage(channelBlock),
            extractMediaThumbnail(channelBlock),
            extractImage(channelBlock),
        )

        val itemMatches = itemRegex.findAll(channelBlock).toList().ifEmpty {
            atomEntryRegex.findAll(channelBlock).toList()
        }

        val episodes = itemMatches.mapNotNull { match ->
            val itemBlock = match.groupValues[1]

            val guidCandidate = firstNonBlank(
                extractTag(itemBlock, "guid"),
                extractTag(itemBlock, "id"),
                extractTag(itemBlock, "link"),
            ) ?: itemBlock.hashCode().toString()

            val itemTitle = firstNonBlank(
                extractTag(itemBlock, "title"),
                extractTag(itemBlock, "itunes:title"),
                guidCandidate,
            )?.let(::sanitizeInlineText) ?: guidCandidate

            val rawDescription = firstNonBlank(
                extractTag(itemBlock, "content:encoded"),
                extractTag(itemBlock, "content"),
                extractTag(itemBlock, "itunes:summary"),
                extractTag(itemBlock, "itunes:subtitle"),
                extractTag(itemBlock, "summary"),
                extractTag(itemBlock, "description"),
            )
            val itemDescription = rawDescription?.let(::sanitizeRichText) ?: ""

            val audioUrl = extractAudioUrl(itemBlock) ?: ""
            if (audioUrl.isBlank()) return@mapNotNull null

            val publishDate = parseDate(
                firstNonBlank(
                    extractTag(itemBlock, "pubDate"),
                    extractTag(itemBlock, "published"),
                    extractTag(itemBlock, "dc:date"),
                    extractTag(itemBlock, "updated"),
                    extractTag(itemBlock, "itunes:releaseDate"),
                ),
            )

            val duration = parseDuration(
                firstNonBlank(
                    extractTag(itemBlock, "itunes:duration"),
                    extractTag(itemBlock, "duration"),
                ),
            )

            val episodeImage = firstNonBlank(
                extractItunesImage(itemBlock),
                extractMediaThumbnail(itemBlock),
                extractImage(itemBlock),
                artworkUrl,
            )

            val chapters = parseChapters(itemBlock)

            RssEpisode(
                id = generateEpisodeId(feedUrl, guidCandidate),
                title = itemTitle,
                description = itemDescription,
                audioUrl = audioUrl,
                publishDate = publishDate,
                duration = duration,
                imageUrl = episodeImage,
                chapters = chapters,
            )
        }.toList()

        val feedUpdated = parseDateOrNull(
            firstNonBlank(
                extractTag(channelBlock, "lastBuildDate"),
                extractTag(channelBlock, "pubDate"),
                extractTag(channelBlock, "updated"),
            ),
        ) ?: episodes.maxByOrNull { it.publishDate }?.publishDate ?: Clock.System.now()

        return PodcastFeed(
            id = generateUniquePodcastId(title, feedUrl),
            title = title,
            description = description,
            artworkUrl = artworkUrl,
            feedUrl = feedUrl,
            lastUpdated = feedUpdated,
            episodes = episodes,
        )
    }

    private fun extractTag(block: String, tag: String): String? {
        // Escape special regex characters in the tag name (especially colon for namespaced tags)
        val escapedTag = Regex.escape(tag)
        val pattern = Regex(
            "<$escapedTag(?:\\s[^>]*)?>([\\s\\S]*?)</$escapedTag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        val cdata = cdataRegex.find(raw)?.groupValues?.get(1) ?: raw
        val decoded = decodeXmlEntities(cdata)
        return decoded.trim().takeIf { it.isNotEmpty() }
    }

    private fun extractAttribute(block: String, tag: String, attribute: String): String? {
        // Escape special regex characters in the tag name
        val escapedTag = Regex.escape(tag)
        val pattern = Regex(
            "<$escapedTag[^>]*\\b$attribute\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]*/?>",
            setOf(RegexOption.IGNORE_CASE)
        )
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        return decodeXmlEntities(raw).trim().takeIf { it.isNotEmpty() }
    }

    private fun extractImage(block: String): String? =
        extractTag(block, "image")?.let { imageBlock ->
            extractTag(imageBlock, "url") ?: extractAttribute(imageBlock, "image", "href")
        }

    private fun extractItunesImage(block: String): String? =
        extractAttribute(block, "itunes:image", "href")

    private fun extractMediaThumbnail(block: String): String? =
        extractAttribute(block, "media:thumbnail", "url")

    private fun extractAudioUrl(block: String): String? {
        val enclosureUrl = extractAttribute(block, "enclosure", "url")
        val enclosureType = extractAttribute(block, "enclosure", "type")
        if (isAudioCandidate(enclosureUrl, enclosureType)) return enclosureUrl

        val mediaUrl = extractAttribute(block, "media:content", "url")
        val mediaType = extractAttribute(block, "media:content", "type")
        if (isAudioCandidate(mediaUrl, mediaType)) return mediaUrl

        val sourceUrl = extractAttribute(block, "media:content", "file")
        if (isAudioCandidate(sourceUrl, mediaType)) return sourceUrl

        val atomUrl = extractEnclosureLinkAttribute(block, "href")
        val atomType = extractEnclosureLinkAttribute(block, "type")
        if (isAudioCandidate(atomUrl, atomType)) return atomUrl

        val link = extractTag(block, "link")
        return link?.takeIf { isAudioCandidate(it, null) }
    }

    private fun extractEnclosureLinkAttribute(block: String, attribute: String): String? {
        val match = enclosureLinkRegex.find(block) ?: return null
        val tagName = match.groupValues[1]
        return extractAttribute(match.value, tagName, attribute)
    }

    private fun isAudioCandidate(url: String?, type: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalizedType = type?.lowercase()
        if (normalizedType != null && (
                normalizedType.contains("audio") ||
                normalizedType.contains("mpeg") ||
                normalizedType.contains("mp3")
            )
        ) {
            return true
        }

        val urlWithoutParams = url.substringBefore('?').lowercase()
        return audioExtensions.any { urlWithoutParams.endsWith(it) }
    }

    private fun parseDate(raw: String?): Instant =
        parseDateOrNull(raw) ?: Clock.System.now()

    private fun parseDateOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        return runCatching { Instant.parse(trimmed) }
            .getOrElse {
                parseRfc2822Date(trimmed)
                    ?: parseUnixTimestamp(trimmed)
            }
    }

    private fun parseUnixTimestamp(value: String): Instant? {
        val digits = value.toLongOrNull() ?: return null
        return when {
            value.length >= 13 -> Instant.fromEpochMilliseconds(digits)
            value.length >= 10 -> Instant.fromEpochSeconds(digits)
            else -> null
        }
    }

    private fun parseRfc2822Date(dateString: String): Instant? {
        // RFC 2822 format: "Wed, 27 Aug 2025 09:44:16 GMT"
        // or: "27 Aug 2025 09:44:16 GMT" (without day of week)

        return runCatching {
            // Remove leading day of week if present (e.g., "Wed, ")
            val cleaned = dateString.replace(Regex("^\\w+,\\s*"), "").trim()

            // Split by whitespace
            val parts = cleaned.split(Regex("\\s+"))
            if (parts.size < 5) return null

            // Parse: "27 Aug 2025 09:44:16 GMT" or "27 Aug 2025 09:44:16 +0800"
            val day = parts[0].toIntOrNull() ?: return null
            val monthName = parts[1]
            val year = parts[2].toIntOrNull() ?: return null

            // Parse time "09:44:16"
            val timeParts = parts[3].split(":")
            if (timeParts.size != 3) return null
            val hour = timeParts[0].toIntOrNull() ?: return null
            val minute = timeParts[1].toIntOrNull() ?: return null
            val second = timeParts[2].toIntOrNull() ?: return null

            // Parse timezone (optional, defaults to UTC)
            val timezone = if (parts.size > 4) parts[4] else "UTC"

            // Get month number
            val month = monthMap[monthName] ?: return null

            // Create LocalDateTime
            val localDateTime = LocalDateTime(year, month, day, hour, minute, second)

            // Parse timezone
            val timeZone = parseTimezoneToken(timezone)

            // Convert to Instant
            localDateTime.toInstant(timeZone)
        }.getOrNull()
    }

    private fun parseTimezoneToken(token: String?): TimeZone {
        if (token.isNullOrBlank()) return TimeZone.UTC
        val trimmed = token.trim()
        return when {
            trimmed.equals("UT", ignoreCase = true) ||
                    trimmed.equals("UTC", ignoreCase = true) ||
                    trimmed.equals("GMT", ignoreCase = true) -> TimeZone.UTC

            trimmed.matches(offsetRegex) -> {
                val sign = if (trimmed.startsWith("-")) -1 else 1
                val hours = trimmed.substring(1, 3).toIntOrNull() ?: 0
                val minutes = trimmed.substring(3, 5).toIntOrNull() ?: 0
                val totalMinutes = sign * (hours * 60 + minutes)
                val offsetHours = totalMinutes / 60
                val offsetMinutes = abs(totalMinutes % 60)
                val prefix = if (offsetHours >= 0) "+" else "-"
                val zoneId = "UTC$prefix${abs(offsetHours).toString().padStart(2, '0')}:${offsetMinutes.toString().padStart(2, '0')}"
                runCatching { TimeZone.of(zoneId) }.getOrDefault(TimeZone.UTC)
            }

            else -> runCatching { TimeZone.of(trimmed) }.getOrDefault(TimeZone.UTC)
        }
    }

    private fun parseDuration(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        val isoMatch = isoDurationRegex.matchEntire(trimmed)
        if (isoMatch != null) {
            val hours = isoMatch.groupValues[2].toLongOrNull() ?: 0
            val minutes = isoMatch.groupValues[3].toLongOrNull() ?: 0
            val seconds = isoMatch.groupValues[4].toLongOrNull() ?: 0
            return ((hours * 3600) + (minutes * 60) + seconds) * 1000
        }

        val parts = trimmed.split(":")
        return try {
            when (parts.size) {
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
                2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
                else -> trimmed.toLong() * 1000
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun generateEpisodeId(feedUrl: String, guid: String): String {
        val trimmed = guid.trim()
        if (trimmed.startsWith("http", ignoreCase = true)) {
            return trimmed
        }
        val seed = "${feedUrl.trim()}::${trimmed}"
        return seed.hashCode().toString().replace("-", "n")
    }

    private fun sanitizeInlineText(raw: String): String =
        inlineWhitespaceRegex.replace(raw, " ").trim()

    private fun sanitizeRichText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = decodeXmlEntities(raw)
        text = scriptRegex.replace(text, " ")
        text = styleRegex.replace(text, " ")
        text = breakTagRegex.replace(text, "\n")
        text = blockTagRegex.replace(text, "\n\n")
        text = htmlTagRegex.replace(text, " ")

        return text
            .replace("\r", "\n")
            .split('\n')
            .map { line -> line.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun decodeXmlEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        val namedDecoded = namedEntities.entries.fold(raw) { acc, (entity, value) ->
            acc.replace(entity, value)
        }
        return numericEntityRegex.replace(namedDecoded) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x", ignoreCase = true)) {
                value.substring(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            } ?: return@replace ""
            Char(codePoint).toString()
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun generateUniquePodcastId(title: String, feedUrl: String): String {
        val urlHash = feedUrl.hashCode().toString().replace("-", "n")
        val titleHash = title.lowercase()
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(30)
        return "podcast-${urlHash}-${titleHash}"
    }

    /**
     * Parse chapters from an episode item block.
     * Supports:
     * 1. Podcast Namespace: <podcast:chapters url="..." type="application/json+chapters" />
     * 2. Podlove Simple Chapters: <psc:chapters><psc:chapter start="..." title="..." /></psc:chapters>
     */
    private fun parseChapters(itemBlock: String): List<Chapter> {
        // Try Podlove Simple Chapters first (embedded in RSS)
        val podloveChapters = parsePodloveSimpleChapters(itemBlock)
        if (podloveChapters.isNotEmpty()) {
            return podloveChapters
        }

        // Podcast Namespace chapters would require HTTP fetching
        // which we'll skip for now as it needs async support
        // TODO: Implement podcast:chapters URL fetching when needed

        return emptyList()
    }

    /**
     * Parse Podlove Simple Chapters format:
     * <psc:chapters version="1.2" xmlns:psc="http://podlove.org/simple-chapters">
     *   <psc:chapter start="00:00:00.000" title="Opening" />
     *   <psc:chapter start="00:03:00.500" title="Topic 1" href="..." image="..." />
     * </psc:chapters>
     */
    private fun parsePodloveSimpleChapters(itemBlock: String): List<Chapter> {
        // Extract the psc:chapters block
        val chaptersBlock = extractTag(itemBlock, "psc:chapters")

        if (chaptersBlock == null) {
            println("⚠️ RSS Parser: psc:chapters tag not found in item block")
            // Debug: check if the tag exists at all
            if (itemBlock.contains("<psc:chapters")) {
                println("⚠️ RSS Parser: Found <psc:chapters in block but extractTag failed")
                println("First 500 chars of itemBlock: ${itemBlock.take(500)}")
            }
            return emptyList()
        }

        println("✓ RSS Parser: Found psc:chapters block, length: ${chaptersBlock.length}")

        // Find all psc:chapter tags
        val chapterMatches = pscChapterRegex.findAll(chaptersBlock).toList()
        if (chapterMatches.isEmpty()) {
            println("⚠️ RSS Parser: No psc:chapter tags found in chapters block")
            println("Chapters block content: $chaptersBlock")
            return emptyList()
        }

        println("✓ RSS Parser: Found ${chapterMatches.size} chapter tags")

        return chapterMatches.mapNotNull { match ->
            val chapterTag = match.value

            // Extract start time (required)
            val startTimeStr = extractAttribute(chapterTag, "psc:chapter", "start")
            if (startTimeStr == null) {
                println("⚠️ RSS Parser: Failed to extract start time from: $chapterTag")
                return@mapNotNull null
            }

            val startTimeMs = parsePodloveTimeToMillis(startTimeStr)
            if (startTimeMs == null) {
                println("⚠️ RSS Parser: Failed to parse time: $startTimeStr")
                return@mapNotNull null
            }

            // Extract title (required)
            val title = extractAttribute(chapterTag, "psc:chapter", "title")
            if (title == null) {
                println("⚠️ RSS Parser: Failed to extract title from: $chapterTag")
                return@mapNotNull null
            }

            // Extract optional attributes
            val imageUrl = extractAttribute(chapterTag, "psc:chapter", "image")
            val url = extractAttribute(chapterTag, "psc:chapter", "href")

            println("✓ RSS Parser: Parsed chapter - $startTimeMs ms: $title")

            Chapter(
                startTimeMs = startTimeMs,
                title = title,
                imageUrl = imageUrl,
                url = url,
            )
        }
    }

    /**
     * Parse Podlove time format (HH:MM:SS.mmm) to milliseconds
     * Examples: "00:00:00.000", "00:03:00.500", "01:15:30.250"
     */
    private fun parsePodloveTimeToMillis(timeStr: String): Long? {
        val parts = timeStr.split(":")
        if (parts.size != 3) return null

        return try {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0

            // Handle seconds with optional milliseconds
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].toLongOrNull() ?: 0
            val millis = if (secondsParts.size > 1) {
                // Pad or truncate to 3 digits
                val millisStr = secondsParts[1].take(3).padEnd(3, '0')
                millisStr.toLongOrNull() ?: 0
            } else {
                0
            }

            (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val channelRegex = Regex(
            "<channel>([\\s\\S]*?)</channel>",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        private val itemRegex = Regex(
            "<item>([\\s\\S]*?)</item>",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        private val atomEntryRegex = Regex(
            "<entry>([\\s\\S]*?)</entry>",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        private val cdataRegex = Regex(
            "<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>",
            setOf(RegexOption.MULTILINE)
        )
        private val breakTagRegex = Regex("<br\\s*/?>", setOf(RegexOption.IGNORE_CASE))
        private val blockTagRegex = Regex(
            "</?(?:p|div|li|blockquote|section|article|h[1-6]|tr|td|th)[^>]*>",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val htmlTagRegex = Regex("<[^>]+>", setOf(RegexOption.IGNORE_CASE))
        private val scriptRegex = Regex(
            "<script[^>]*>[\\s\\S]*?</script>",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val styleRegex = Regex(
            "<style[^>]*>[\\s\\S]*?</style>",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val inlineWhitespaceRegex = Regex("\\s+")
        private val numericEntityRegex = Regex("&#(x?[0-9A-Fa-f]+);")
        private val offsetRegex = Regex("[+-]\\d{4}")
        private val isoDurationRegex = Regex("^P(T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)$", RegexOption.IGNORE_CASE)
        private val enclosureLinkRegex = Regex(
            "<((?:\\w+:)?link)[^>]*\\brel\\s*=\\s*['\"]enclosure['\"][^>]*>",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val pscChapterRegex = Regex(
            "<psc:chapter[^>]*/>",
            setOf(RegexOption.IGNORE_CASE)
        )

        private val audioExtensions = listOf(
            ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav"
        )

        private val namedEntities = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&nbsp;" to " ",
            "&ndash;" to "–",
            "&mdash;" to "—",
            "&hellip;" to "…",
            "&lsquo;" to "'",
            "&rsquo;" to "'",
            "&ldquo;" to "\"",
            "&rdquo;" to "\"",
        )

        private val monthMap = mapOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
        )
    }
}
