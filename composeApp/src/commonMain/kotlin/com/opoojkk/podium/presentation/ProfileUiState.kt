package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Podcast

/**
 * UI model for the profile ("我的") screen.
 */
data class ProfileUiState(
    val subscribedPodcasts: List<Podcast> = emptyList(),
    val cacheSizeInMb: Int = 0,
    val cachedDownloads: List<ProfileCachedItem> = emptyList(),
    val inProgressDownloads: List<ProfileDownloadItem> = emptyList(),
    val queuedDownloads: List<ProfileDownloadItem> = emptyList(),
    val updateInterval: com.opoojkk.podium.data.model.UpdateInterval = com.opoojkk.podium.data.model.UpdateInterval.DAILY,
)

data class ProfileDownloadItem(
    val episodeId: String,
    val episodeTitle: String,
    val podcastTitle: String,
    val podcastArtworkUrl: String?,
    val status: DownloadStatus,
)

data class ProfileCachedItem(
    val episodeId: String,
    val episodeTitle: String,
    val podcastTitle: String,
    val podcastArtworkUrl: String?,
    val sizeBytes: Long,
    val filePath: String?,
    val completedAtMillis: Long?,
)
