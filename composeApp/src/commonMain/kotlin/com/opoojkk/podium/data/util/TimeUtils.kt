package com.opoojkk.podium.data.util

import kotlinx.datetime.*
import kotlin.math.abs

/**
 * Time parsing and formatting utilities.
 * Supports parsing of various time formats including RFC 2822, Unix timestamps, ISO 8601, etc.
 */
object TimeUtils {

    private val monthMap = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )

    private val offsetRegex = Regex("[+-]\\d{4}")
    private val isoDurationRegex = Regex("^P(T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)$", RegexOption.IGNORE_CASE)

    /**
     * Parse date string, returning current time on failure.
     *
     * @param raw Date string
     * @return Parsed Instant, or current time on failure
     */
    fun parseDate(raw: String?): Instant =
        parseDateOrNull(raw) ?: Clock.System.now()

    /**
     * Attempt to parse date string, supporting multiple formats.
     * Supported formats:
     * 1. ISO 8601 format
     * 2. RFC 2822 format
     * 3. Unix timestamp (seconds or milliseconds)
     *
     * @param raw Date string
     * @return Parsed Instant, or null on failure
     */
    fun parseDateOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        return runCatching { Instant.parse(trimmed) }
            .getOrElse {
                parseRfc2822Date(trimmed)
                    ?: parseUnixTimestamp(trimmed)
            }
    }

    /**
     * Parse Unix timestamp (supports seconds and milliseconds).
     *
     * @param value Timestamp string
     * @return Parsed Instant, or null on failure
     */
    fun parseUnixTimestamp(value: String): Instant? {
        val digits = value.toLongOrNull() ?: return null
        return when {
            value.length >= 13 -> Instant.fromEpochMilliseconds(digits)
            value.length >= 10 -> Instant.fromEpochSeconds(digits)
            else -> null
        }
    }

    /**
     * Parse RFC 2822 format date.
     * Examples: "Wed, 27 Aug 2025 09:44:16 GMT"
     * or: "27 Aug 2025 09:44:16 GMT" (without day of week)
     *
     * @param dateString Date string
     * @return Parsed Instant, or null on failure
     */
    fun parseRfc2822Date(dateString: String): Instant? {
        return runCatching {
            // Remove leading day of week (e.g., "Wed, ")
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

    /**
     * Parse timezone token.
     *
     * @param token Timezone string (e.g., "GMT", "+0800", etc.)
     * @return TimeZone object
     */
    fun parseTimezoneToken(token: String?): TimeZone {
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

    /**
     * Parse duration string.
     * Supported formats:
     * 1. ISO 8601 duration format (e.g., "PT1H30M45S")
     * 2. Hours:minutes:seconds format (e.g., "1:30:45")
     * 3. Minutes:seconds format (e.g., "30:45")
     * 4. Pure seconds (e.g., "1845")
     *
     * @param raw Duration string
     * @return Duration in milliseconds, or null on failure
     */
    fun parseDuration(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // Try parsing ISO 8601 format
        val isoMatch = isoDurationRegex.matchEntire(trimmed)
        if (isoMatch != null) {
            val hours = isoMatch.groupValues[2].toLongOrNull() ?: 0
            val minutes = isoMatch.groupValues[3].toLongOrNull() ?: 0
            val seconds = isoMatch.groupValues[4].toLongOrNull() ?: 0
            return ((hours * 3600) + (minutes * 60) + seconds) * 1000
        }

        // Try parsing HH:MM:SS or MM:SS or pure seconds format
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

    /**
     * Parse Podlove time format to milliseconds.
     * Supported formats: "HH:MM:SS.mmm" or "MM:SS.mmm" or "SS.mmm"
     *
     * @param timeString Podlove format time string
     * @return Time in milliseconds, or null on failure
     */
    fun parsePodloveTimeToMillis(timeString: String): Long? {
        if (timeString.isBlank()) return null

        return try {
            val parts = timeString.split(":")
            when (parts.size) {
                3 -> {
                    // HH:MM:SS.mmm
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secondsParts = parts[2].split(".")
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else {
                        0L
                    }
                    (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
                }
                2 -> {
                    // MM:SS.mmm
                    val minutes = parts[0].toLong()
                    val secondsParts = parts[1].split(".")
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else {
                        0L
                    }
                    (minutes * 60 + seconds) * 1000 + millis
                }
                1 -> {
                    // SS.mmm
                    val secondsParts = parts[0].split(".")
                    val seconds = secondsParts[0].toLong()
                    val millis = if (secondsParts.size > 1) {
                        secondsParts[1].padEnd(3, '0').take(3).toLong()
                    } else {
                        0L
                    }
                    seconds * 1000 + millis
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format milliseconds to readable duration string.
     *
     * @param milliseconds Milliseconds
     * @return Formatted duration string (e.g., "1:30:45")
     */
    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
}
