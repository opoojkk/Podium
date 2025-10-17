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
)

/**
 * Playback progress persisted for each episode.
 */
data class PlaybackProgress(
    val episodeId: String,
    val positionMs: Long,
    val updatedAt: Instant,
)

/**
 * Wrapper used by the UI to display an episode together with its podcast metadata.
 */
data class EpisodeWithPodcast(
    val episode: Episode,
    val podcast: Podcast,
)

/**
 * State of the current playback session.
 */
data class PlaybackState(
    val episode: Episode?,
    val positionMs: Long,
    val isPlaying: Boolean,
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
