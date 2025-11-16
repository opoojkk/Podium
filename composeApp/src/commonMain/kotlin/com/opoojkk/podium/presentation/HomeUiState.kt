package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast

/**
 * 搜索结果筛选类型
 */
enum class SearchFilterType {
    ALL,        // 全部
    PODCASTS,   // 播客节目
    EPISODES    // 单集
}

/**
 * UI state for the home screen containing recent listening and updates.
 */
data class HomeUiState(
    val recentPlayed: List<EpisodeWithPodcast> = emptyList(),
    val recommendedEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val recentUpdates: List<EpisodeWithPodcast> = emptyList(),
    // XYZRank data - converted to standard models
    val hotEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val hotPodcasts: List<Podcast> = emptyList(),
    val newEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val newPodcasts: List<Podcast> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchResults: List<EpisodeWithPodcast> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
    val searchFilterType: SearchFilterType = SearchFilterType.ALL,
    // 分页相关字段
    val searchOffset: Int = 0,
    val searchLimit: Int = 20,
    val hasMoreSearchResults: Boolean = true,
    val isLoadingMoreResults: Boolean = false,
)
