package com.opoojkk.podium.data.subscription

/**
 * Service for importing podcast subscriptions from various standard formats.
 * Supports OPML 2.0 and JSON formats.
 */
class SubscriptionImporter {

    /**
     * Import result containing the list of feed URLs and metadata.
     */
    data class ImportResult(
        val feedUrls: List<FeedInfo>,
        val format: ImportFormat,
    )

    data class FeedInfo(
        val feedUrl: String,
        val title: String? = null,
        val description: String? = null,
        val artworkUrl: String? = null,
        val autoDownload: Boolean = false,
    )

    enum class ImportFormat {
        OPML,
        JSON,
        UNKNOWN
    }

    /**
     * Detect format and import subscriptions.
     */
    fun import(content: String): ImportResult {
        val trimmed = content.trim()

        return when {
            trimmed.startsWith("<?xml") || trimmed.contains("<opml") -> {
                ImportResult(
                    feedUrls = importFromOpml(trimmed),
                    format = ImportFormat.OPML,
                )
            }

            trimmed.startsWith("{") -> {
                ImportResult(
                    feedUrls = importFromJson(trimmed),
                    format = ImportFormat.JSON,
                )
            }

            else -> {
                // Try OPML as fallback
                ImportResult(
                    feedUrls = importFromOpml(trimmed),
                    format = ImportFormat.UNKNOWN,
                )
            }
        }
    }

    /**
     * Import subscriptions from OPML format.
     */
    fun importFromOpml(opml: String): List<FeedInfo> {
        val urls = mutableListOf<FeedInfo>()
        val outlineMatches = outlineTagRegex.findAll(opml)

        for (match in outlineMatches) {
            val attributes = extractAttributes(match.value)
            val xmlUrl = attributes["xmlurl"] ?: attributes["url"] ?: attributes["rssurl"]

            if (!xmlUrl.isNullOrBlank()) {
                val title = attributes["title"] ?: attributes["text"]
                val description = attributes["description"]
                val artworkUrl = attributes["imageurl"] ?: attributes["image"]

                urls.add(
                    FeedInfo(
                        feedUrl = xmlUrl.trim(),
                        title = title?.trim(),
                        description = description?.trim(),
                        artworkUrl = artworkUrl?.trim(),
                    )
                )
            }
        }

        return urls
    }

    /**
     * Import subscriptions from JSON format.
     */
    fun importFromJson(json: String): List<FeedInfo> {
        val feeds = mutableListOf<FeedInfo>()

        try {
            // Simple JSON parsing without external library
            val subscriptionsMatch = subscriptionsArrayRegex.find(json) ?: return emptyList()
            val subscriptionsArray = subscriptionsMatch.groupValues[1]

            val objectMatches = jsonObjectRegex.findAll(subscriptionsArray)

            for (objectMatch in objectMatches) {
                val obj = objectMatch.value
                val feedUrl = extractJsonField(obj, "feedUrl")
                val title = extractJsonField(obj, "title")
                val description = extractJsonField(obj, "description")
                val artworkUrl = extractJsonField(obj, "artworkUrl")
                val autoDownload = extractJsonField(obj, "autoDownload")

                if (!feedUrl.isNullOrBlank()) {
                    feeds.add(
                        FeedInfo(
                            feedUrl = feedUrl.trim(),
                            title = title?.trim(),
                            description = description?.trim(),
                            artworkUrl = artworkUrl?.trim(),
                            autoDownload = autoDownload?.equals("true", ignoreCase = true) ?: false,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("Failed to parse JSON: ${e.message}")
            return emptyList()
        }

        return feeds
    }

    private fun extractAttributes(tag: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val matches = attributeRegex.findAll(tag)

        for (match in matches) {
            val key = match.groupValues[1].lowercase()
            val value = decodeXmlEntities(match.groupValues[3])
            attributes[key] = value
        }

        return attributes
    }

    private fun extractJsonField(obj: String, fieldName: String): String? {
        val pattern = Regex(""""$fieldName"\s*:\s*"([^"]*)"""")
        val match = pattern.find(obj) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\b", "\b")
    }

    private fun decodeXmlEntities(raw: String): String {
        val namedDecoded = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

        return numericEntityRegex.replace(namedDecoded) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x") || value.startsWith("X")) {
                value.substring(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            }
            codePoint?.let { cp -> codePointToString(cp) } ?: match.value
        }
    }

    private fun codePointToString(codePoint: Int): String? = when {
        codePoint < 0 -> null
        codePoint <= 0xFFFF -> runCatching { codePoint.toChar().toString() }.getOrNull()
        codePoint <= 0x10FFFF -> {
            val high = ((codePoint - 0x10000) shr 10) + 0xD800
            val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
            if (high in 0xD800..0xDBFF && low in 0xDC00..0xDFFF) {
                buildString(2) {
                    append(high.toChar())
                    append(low.toChar())
                }
            } else {
                null
            }
        }
        else -> null
    }

    companion object {
        private val outlineTagRegex = Regex(
            "<outline\\b[^>]*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )

        private val attributeRegex = Regex(
            """([A-Za-z_:][\\w:.-]*)\s*=\s*(['"])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE)
        )

        private val numericEntityRegex = Regex("&#(x?[0-9A-Fa-f]+);")

        private val subscriptionsArrayRegex = Regex(
            """"subscriptions"\s*:\s*\[([\s\S]*?)\]"""
        )

        private val jsonObjectRegex = Regex(
            """\{[^}]+\}"""
        )
    }
}
