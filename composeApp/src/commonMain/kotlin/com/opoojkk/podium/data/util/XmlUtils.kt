package com.opoojkk.podium.data.util

/**
 * XML entity encoding and decoding utilities.
 * Provides unified handling of XML special character escaping and unescaping.
 */
object XmlUtils {

    private val namedEntities = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&amp;" to "&"
    )

    private val numericEntityRegex = Regex("&#(x?[0-9a-fA-F]+);")

    /**
     * Decode XML-encoded string to raw string.
     * Supports both named entities (e.g., &lt;) and numeric entities (e.g., &#38; or &#x26;).
     *
     * @param raw String to decode
     * @return Decoded string
     */
    fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw

        // Process named entities first
        val namedDecoded = namedEntities.entries.fold(raw) { acc, (entity, value) ->
            acc.replace(entity, value)
        }

        // Then process numeric entities
        return numericEntityRegex.replace(namedDecoded) { match ->
            val value = match.groupValues[1]
            val codePoint = if (value.startsWith("x", ignoreCase = true)) {
                value.substring(1).toIntOrNull(16)
            } else {
                value.toIntOrNull()
            }

            codePoint?.let { cp -> codePointToString(cp) } ?: match.value
        }
    }

    /**
     * Encode raw string to XML-safe string.
     * Escapes special characters: & < > " '
     *
     * @param raw String to encode
     * @return Encoded string
     */
    fun encodeEntities(raw: String): String =
        buildString(raw.length + 16) {
            raw.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }

    /**
     * Convert Unicode code point to string.
     * Supports both Basic Multilingual Plane (BMP) and supplementary plane characters.
     *
     * @param codePoint Unicode code point
     * @return Corresponding string, or null if code point is invalid
     */
    private fun codePointToString(codePoint: Int): String? = when {
        codePoint < 0 -> null
        codePoint <= 0xFFFF -> runCatching {
            codePoint.toChar().toString()
        }.getOrNull()
        codePoint <= 0x10FFFF -> {
            // Handle supplementary plane characters (using surrogate pairs)
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
}
