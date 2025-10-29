package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.PlaylistItem

/**
 * UI state for the playlist screen containing episodes that are in progress.
 */
data class PlaylistUiState(
    val items: List<PlaylistItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
