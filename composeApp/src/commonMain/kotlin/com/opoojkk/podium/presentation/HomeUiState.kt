package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.EpisodeWithPodcast

/**
 * UI state for the home screen containing recent listening and updates.
 */
data class HomeUiState(
    val recentPlayed: List<EpisodeWithPodcast> = emptyList(),
    val recentUpdates: List<EpisodeWithPodcast> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
