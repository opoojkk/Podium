package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.recommended.RecommendedPodcast

/**
 * UI state for the home screen containing recent listening and updates.
 */
data class HomeUiState(
    val recentPlayed: List<EpisodeWithPodcast> = emptyList(),
    val recommendedEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val recommendedPodcasts: List<Pair<RecommendedPodcast, String>> = emptyList(),
    val recentUpdates: List<EpisodeWithPodcast> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<EpisodeWithPodcast> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
)
