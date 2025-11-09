package com.opoojkk.podium.data.model.recommended

import kotlinx.serialization.Serializable

@Serializable
data class PodcastCollection(
    val version: String,
    val lastUpdated: String,
    val description: String,
    val categories: List<PodcastCategory>,
)

@Serializable
data class PodcastCategory(
    val id: String,
    val name: String,
    val description: String,
    val podcasts: List<RecommendedPodcast>,
)

@Serializable
data class RecommendedPodcast(
    val id: String,
    val name: String,
    val description: String,
    val host: String? = null,
    val rssUrl: String? = null,
    val website: String? = null,
    val appleId: String? = null,
    val artworkUrl: String? = null,
    val verified: Boolean = false,
)
