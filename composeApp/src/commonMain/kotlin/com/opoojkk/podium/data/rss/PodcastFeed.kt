package com.opoojkk.podium.data.rss

import kotlinx.datetime.Instant

data class PodcastFeed(
    val id: String,
    val title: String,
    val description: String,
    val artworkUrl: String?,
    val feedUrl: String,
    val lastUpdated: Instant,
    val episodes: List<RssEpisode>,
)

data class RssEpisode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDate: Instant,
    val duration: Long?,
    val imageUrl: String?,
)
