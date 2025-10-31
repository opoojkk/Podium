package com.opoojkk.podium.data.model

import kotlinx.datetime.Instant

/**
 * Domain model representing a subscribed podcast feed.
 */
data class Podcast(
    val id: String,
    val title: String,
    val description: String,
    val artworkUrl: String?,
    val feedUrl: String,
    val lastUpdated: Instant,
    val autoDownload: Boolean,
)

/**
 * Domain model for an individual podcast episode.
 */
data class Episode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDate: Instant,
    val duration: Long?,
    val imageUrl: String?,
    val chapters: List<Chapter> = emptyList(),
)

/**
 * Chapter information for podcast episodes.
 */
data class Chapter(
    val startTimeMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null,
)

/**
 * Playback progress persisted for each episode.
 */
data class PlaybackProgress(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val updatedAt: Instant,
    val isCompleted: Boolean = false,
    val addedToPlaylist: Boolean = true,
)

/**
 * Wrapper used by the UI to display an episode together with its podcast metadata.
 */
data class EpisodeWithPodcast(
    val episode: Episode,
    val podcast: Podcast,
)

/**
 * Playlist item with episode, podcast and playback progress information.
 */
data class PlaylistItem(
    val episode: Episode,
    val podcast: Podcast,
    val progress: PlaybackProgress,
)

/**
 * State of the current playback session.
 */
data class PlaybackState(
    val episode: Episode?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val durationMs: Long? = null,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
)

sealed interface DownloadStatus {
    val episodeId: String

    data class Idle(override val episodeId: String) : DownloadStatus
    data class InProgress(
        override val episodeId: String,
        val progress: Float,
    ) : DownloadStatus

    data class Completed(
        override val episodeId: String,
        val filePath: String,
    ) : DownloadStatus

    data class Failed(
        override val episodeId: String,
        val reason: String,
    ) : DownloadStatus
}

/**
 * Sleep timer duration options.
 */
enum class SleepTimerDuration(val minutes: Int, val displayName: String) {
    FIVE_MIN(5, "5分钟"),
    FIFTEEN_MIN(15, "15分钟"),
    THIRTY_MIN(30, "30分钟"),
    ONE_HOUR(60, "1小时");

    val milliseconds: Long get() = minutes * 60 * 1000L
}

/**
 * State of the sleep timer.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val duration: SleepTimerDuration? = null,
    val remainingMs: Long = 0L,
) {
    val remainingMinutes: Int get() = (remainingMs / 60000).toInt()
    val remainingSeconds: Int get() = ((remainingMs % 60000) / 1000).toInt()
}
