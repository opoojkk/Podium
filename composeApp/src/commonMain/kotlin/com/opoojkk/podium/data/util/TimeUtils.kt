package com.opoojkk.podium.data.util

import kotlinx.datetime.*
import kotlin.math.abs

/**
 * 时间解析和格式化工具类
 * 支持多种时间格式的解析，包括 RFC 2822、Unix 时间戳、ISO 8601 等
 */
object TimeUtils {

    private val monthMap = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )

    private val offsetRegex = Regex("[+-]\\d{4}")
    private val isoDurationRegex = Regex("^P(T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)$", RegexOption.IGNORE_CASE)

    /**
     * 解析日期字符串，失败时返回当前时间
     *
     * @param raw 日期字符串
     * @return 解析后的 Instant，失败时返回当前时间
     */
    fun parseDate(raw: String?): Instant =
        parseDateOrNull(raw) ?: Clock.System.now()

    /**
     * 尝试解析日期字符串，支持多种格式
     * 支持的格式：
     * 1. ISO 8601 格式
     * 2. RFC 2822 格式
     * 3. Unix 时间戳（秒或毫秒）
     *
     * @param raw 日期字符串
     * @return 解析后的 Instant，失败时返回 null
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
     * 解析 Unix 时间戳（支持秒和毫秒）
     *
     * @param value 时间戳字符串
     * @return 解析后的 Instant，失败时返回 null
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
     * 解析 RFC 2822 格式的日期
     * 例如："Wed, 27 Aug 2025 09:44:16 GMT"
     * 或："27 Aug 2025 09:44:16 GMT"（不带星期）
     *
     * @param dateString 日期字符串
     * @return 解析后的 Instant，失败时返回 null
     */
    fun parseRfc2822Date(dateString: String): Instant? {
        return runCatching {
            // 移除开头的星期（如 "Wed, "）
            val cleaned = dateString.replace(Regex("^\\w+,\\s*"), "").trim()

            // 按空格分割
            val parts = cleaned.split(Regex("\\s+"))
            if (parts.size < 5) return null

            // 解析："27 Aug 2025 09:44:16 GMT" 或 "27 Aug 2025 09:44:16 +0800"
            val day = parts[0].toIntOrNull() ?: return null
            val monthName = parts[1]
            val year = parts[2].toIntOrNull() ?: return null

            // 解析时间 "09:44:16"
            val timeParts = parts[3].split(":")
            if (timeParts.size != 3) return null
            val hour = timeParts[0].toIntOrNull() ?: return null
            val minute = timeParts[1].toIntOrNull() ?: return null
            val second = timeParts[2].toIntOrNull() ?: return null

            // 解析时区（可选，默认为 UTC）
            val timezone = if (parts.size > 4) parts[4] else "UTC"

            // 获取月份数字
            val month = monthMap[monthName] ?: return null

            // 创建 LocalDateTime
            val localDateTime = LocalDateTime(year, month, day, hour, minute, second)

            // 解析时区
            val timeZone = parseTimezoneToken(timezone)

            // 转换为 Instant
            localDateTime.toInstant(timeZone)
        }.getOrNull()
    }

    /**
     * 解析时区标记
     *
     * @param token 时区字符串（如 "GMT"、"+0800" 等）
     * @return TimeZone 对象
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
     * 解析持续时间字符串
     * 支持的格式：
     * 1. ISO 8601 持续时间格式（如 "PT1H30M45S"）
     * 2. 时分秒格式（如 "1:30:45"）
     * 3. 分秒格式（如 "30:45"）
     * 4. 纯秒数（如 "1845"）
     *
     * @param raw 持续时间字符串
     * @return 持续时间（毫秒），失败时返回 null
     */
    fun parseDuration(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // 尝试解析 ISO 8601 格式
        val isoMatch = isoDurationRegex.matchEntire(trimmed)
        if (isoMatch != null) {
            val hours = isoMatch.groupValues[2].toLongOrNull() ?: 0
            val minutes = isoMatch.groupValues[3].toLongOrNull() ?: 0
            val seconds = isoMatch.groupValues[4].toLongOrNull() ?: 0
            return ((hours * 3600) + (minutes * 60) + seconds) * 1000
        }

        // 尝试解析 HH:MM:SS 或 MM:SS 或纯秒数格式
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
     * 解析 Podlove 时间格式为毫秒
     * 支持格式："HH:MM:SS.mmm" 或 "MM:SS.mmm" 或 "SS.mmm"
     *
     * @param timeString Podlove 格式的时间字符串
     * @return 时间（毫秒），失败时返回 null
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
     * 格式化毫秒为可读的时长字符串
     *
     * @param milliseconds 毫秒数
     * @return 格式化的时长字符串（如 "1:30:45"）
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
