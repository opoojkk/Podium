package com.opoojkk.podium.data.util

/**
 * JSON string escaping and unescaping utilities.
 * Provides manual handling of JSON string encoding and decoding.
 */
object JsonUtils {

    /**
     * Encode string to JSON format string literal (including quotes).
     * Handles special character escaping.
     *
     * @param value String to encode
     * @return JSON-formatted string (with quotes)
     */
    fun encodeString(value: String): String {
        return buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 32) {
                        // Encode control characters as \uXXXX format
                        append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }
    }

    /**
     * Decode escape sequences in JSON string.
     * Note: This method does not handle quotes, assumes input has outer quotes removed.
     *
     * @param value String to decode
     * @return Decoded string
     */
    fun decodeString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\b", "\b")
    }

    /**
     * Escape string for JSON value (without outer quotes).
     * Difference from encodeString is that it does not add quotes.
     *
     * @param value String to escape
     * @return Escaped string (without quotes)
     */
    fun escapeString(value: String): String {
        return buildString {
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 32) {
                        append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }
}
