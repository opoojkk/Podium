package com.opoojkk.podium.data.model.xyzrank

import kotlinx.serialization.Serializable

/**
 * Data models for XYZRank API
 * Source: https://github.com/eddiehe99/xyzrank
 */

// Episode models for hot episodes
@Serializable
data class XYZRankEpisode(
    val title: String,
    val podcastID: String,
    val podcastName: String,
    val logoURL: String,
    val link: String,
    val playCount: Int,
    val commentCount: Int,
    val subscription: Int,
    val duration: Int,
    val postTime: String,
    val primaryGenreName: String,
    val totalEpisodesCount: Int,
    val openRate: Double,
    val lastReleaseDateDayCount: Double
)

@Serializable
data class XYZRankEpisodeData(
    val episodes: List<XYZRankEpisode>
)

@Serializable
data class XYZRankEpisodeResponse(
    val data: XYZRankEpisodeData
)

// Podcast models for hot podcasts
@Serializable
data class XYZRankPodcastLink(
    val name: String,
    val url: String?
)

@Serializable
data class XYZRankPodcast(
    val id: String,
    val rank: Int,
    val name: String,
    val logoURL: String,
    val primaryGenreName: String,
    val authorsText: String,
    val trackCount: Int,
    val lastReleaseDate: String,
    val lastReleaseDateDayCount: Double,
    val firstEpisodePostTime: String,
    val activeRate: Double,
    val avgDuration: Double,
    val avgPlayCount: Int,
    val avgUpdateFreq: Double,
    val avgCommentCount: Double,
    val avgInteractIndicator: Double,
    val avgOpenRate: Double,
    val links: List<XYZRankPodcastLink>
)

@Serializable
data class XYZRankPodcastData(
    val podcasts: List<XYZRankPodcast>
)

@Serializable
data class XYZRankPodcastResponse(
    val data: XYZRankPodcastData
)
