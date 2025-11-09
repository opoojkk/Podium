package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.recommended.PodcastCategory

data class CategoriesUiState(
    val categories: List<PodcastCategory> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
