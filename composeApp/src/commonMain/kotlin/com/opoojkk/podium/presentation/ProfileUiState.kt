package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.Podcast

/**
 * UI model for the profile ("我的") screen.
 */
data class ProfileUiState(
    val subscribedPodcasts: List<Podcast> = emptyList(),
    val autoDownload: Boolean = true,
    val cacheSizeInMb: Int = 0,
)
