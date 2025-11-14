package com.opoojkk.podium.data.mapper

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.model.xyzrank.XYZRankEpisode
import com.opoojkk.podium.data.model.xyzrank.XYZRankPodcast
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Mapper functions to convert XYZRank data models to app's domain models
 */

/**
 * Convert XYZRankEpisode to EpisodeWithPodcast
 * Note: XYZRank episodes cannot be played directly as they don't have audio URLs,
 * only web links to platforms like 小宇宙
 */
fun XYZRankEpisode.toEpisodeWithPodcast(): EpisodeWithPodcast {
    val podcast = Podcast(
        id = "xyzrank_podcast_$podcastID",
        title = podcastName,
        description = "来自 XYZRank 热门榜单 · $primaryGenreName\n小宇宙链接：$link",
        artworkUrl = logoURL,
        feedUrl = "", // XYZRank doesn't provide RSS feed URL in episode data
        lastUpdated = parsePostTime(postTime),
        autoDownload = false,
    )

    val episode = Episode(
        id = "xyzrank_episode_${podcastID}_${title.hashCode()}",
        podcastId = podcast.id,
        podcastTitle = podcastName,
        title = title,
        description = buildEpisodeDescription(
            playCount = playCount,
            commentCount = commentCount,
            subscription = subscription,
            openRate = openRate,
            webLink = link
        ),
        audioUrl = "", // XYZRank episodes don't have direct audio URLs
        publishDate = parsePostTime(postTime),
        duration = duration.toLong() * 1000, // Convert seconds to milliseconds
        imageUrl = logoURL,
    )

    return EpisodeWithPodcast(
        episode = episode,
        podcast = podcast,
    )
}

/**
 * Convert XYZRankPodcast to Podcast
 */
fun XYZRankPodcast.toPodcast(): Podcast {
    val xiaoyuzhouLink = links.firstOrNull { it.name.contains("小宇宙", ignoreCase = true) }?.url
    val rssLink = links.firstOrNull { it.name.contains("RSS", ignoreCase = true) }?.url

    return Podcast(
        id = "xyzrank_podcast_$id",
        title = name,
        description = buildPodcastDescription(
            genre = primaryGenreName,
            authors = authorsText,
            trackCount = trackCount,
            avgPlayCount = avgPlayCount,
            avgDuration = avgDuration,
            activeRate = activeRate,
            xiaoyuzhouLink = xiaoyuzhouLink,
            rssLink = rssLink
        ),
        artworkUrl = logoURL,
        feedUrl = rssLink ?: "",
        lastUpdated = parseLastReleaseDate(lastReleaseDate),
        autoDownload = false,
    )
}

/**
 * Build description for episodes from XYZRank stats
 */
private fun buildEpisodeDescription(
    playCount: Int,
    commentCount: Int,
    subscription: Int,
    openRate: Double,
    webLink: String
): String {
    return buildString {
        append("📊 数据统计\n")
        append("播放量：${formatCount(playCount)} · ")
        append("评论：${formatCount(commentCount)} · ")
        append("订阅：${formatCount(subscription)} · ")
        append("打开率：${"%.1f".format(openRate * 100)}%\n\n")
        append("🔗 来源：XYZRank 榜单\n")
        append("💡 提示：此节目来自榜单推荐，点击可在小宇宙中收听\n")
        append("链接：$webLink")
    }
}

/**
 * Build description for podcasts from XYZRank stats
 */
private fun buildPodcastDescription(
    genre: String?,
    authors: String,
    trackCount: Int,
    avgPlayCount: Int,
    avgDuration: Double,
    activeRate: Double,
    xiaoyuzhouLink: String?,
    rssLink: String?
): String {
    return buildString {
        append("📊 播客信息\n")
        genre?.let { append("分类：$it\n") }
        append("作者：$authors\n")
        append("节目数：$trackCount\n")
        append("平均播放量：${formatCount(avgPlayCount)}\n")
        append("平均时长：${formatDuration(avgDuration)}\n")
        append("活跃度：${"%.1f".format(activeRate * 100)}%\n\n")

        append("🔗 来源：XYZRank 榜单\n")
        if (rssLink != null) {
            append("✅ 可订阅：此播客提供 RSS 源，点击可订阅\n")
        } else if (xiaoyuzhouLink != null) {
            append("💡 提示：点击在小宇宙中查看\n")
            append("链接：$xiaoyuzhouLink")
        } else {
            append("💡 提示：此播客来自榜单推荐")
        }
    }
}

/**
 * Parse post time string to Instant
 * Format example: "2024-01-15 10:30:00"
 */
private fun parsePostTime(postTime: String): Instant {
    return try {
        // Try parsing the date string
        // Format: "yyyy-MM-dd HH:mm:ss"
        val parts = postTime.split(" ")
        if (parts.size == 2) {
            val dateParts = parts[0].split("-")
            val timeParts = parts[1].split(":")

            if (dateParts.size == 3 && timeParts.size == 3) {
                val year = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val day = dateParts[2].toInt()
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                val second = timeParts[2].toInt()

                kotlinx.datetime.LocalDateTime(year, month, day, hour, minute, second)
                    .toInstant(TimeZone.UTC)
            } else {
                Clock.System.now()
            }
        } else {
            Clock.System.now()
        }
    } catch (e: Exception) {
        Clock.System.now()
    }
}

/**
 * Parse last release date to Instant
 */
private fun parseLastReleaseDate(lastReleaseDate: String): Instant {
    return parsePostTime(lastReleaseDate)
}

/**
 * Format large numbers (e.g., 12345 -> 12.3K)
 */
private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
        count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
        else -> count.toString()
    }
}

/**
 * Format duration in seconds to readable format (e.g., 3665.5 -> 1h 1m)
 */
private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "${totalSeconds}秒"
    }
}
