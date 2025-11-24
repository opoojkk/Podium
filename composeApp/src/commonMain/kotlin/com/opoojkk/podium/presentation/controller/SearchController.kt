package com.opoojkk.podium.presentation.controller

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.repository.ApplePodcastSearchRepository
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.presentation.SearchFilterType
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for search functionality.
 */
data class SearchState(
    val searchQuery: String = "",
    val searchResults: List<EpisodeWithPodcast> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
    val searchFilterType: SearchFilterType = SearchFilterType.ALL,
    val searchOffset: Int = 0,
    val searchLimit: Int = 20,
    val hasMoreSearchResults: Boolean = true,
    val isLoadingMoreResults: Boolean = false,
)

/**
 * Controller responsible for search-related operations including
 * local search, Apple Podcast search, and search result management.
 */
class SearchController(
    private val repository: PodcastRepository,
    private val applePodcastSearchRepository: ApplePodcastSearchRepository,
    private val scope: CoroutineScope,
) {
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Handle search query changes with debouncing.
     */
    fun onSearchQueryChange(query: String) {
        val sanitizedQuery = query.take(200)
        val effectiveQuery = sanitizedQuery.trim()

        searchJob?.cancel()

        _searchState.update { current ->
            current.copy(
                searchQuery = sanitizedQuery,
                isSearchActive = effectiveQuery.isNotEmpty(),
                searchErrorMessage = null,
                isSearching = effectiveQuery.isNotEmpty(),
                searchResults = if (current.searchQuery == sanitizedQuery) current.searchResults else emptyList(),
                // Reset pagination
                searchOffset = 0,
                hasMoreSearchResults = true,
            )
        }

        if (effectiveQuery.isEmpty()) {
            _searchState.update { current ->
                current.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    isSearchActive = false,
                    searchOffset = 0,
                    hasMoreSearchResults = true,
                )
            }
            return
        }

        searchJob = scope.launch {
            kotlinx.coroutines.delay(250)
            try {
                val limit = _searchState.value.searchLimit
                Logger.d("SearchController") { "开始搜索: \"$effectiveQuery\", limit=$limit" }

                // Parallel search: local, iTunes podcasts, and iTunes episodes
                val localResults = async {
                    repository.searchEpisodes(effectiveQuery, limit = limit, offset = 0)
                }
                val remotePodcastResults = async {
                    searchApplePodcasts(effectiveQuery, limit = 10)
                }
                val remoteEpisodeResults = async {
                    searchAppleEpisodes(effectiveQuery, limit = limit)
                }

                // Wait for all searches to complete
                val local = localResults.await()
                val remotePodcasts = remotePodcastResults.await()
                val remoteEpisodes = remoteEpisodeResults.await()

                Logger.i("SearchController") { "搜索完成 - 本地: ${local.size}, iTunes播客: ${remotePodcasts.size}, iTunes单集: ${remoteEpisodes.size}" }

                // Combine results: iTunes podcasts first, then iTunes episodes, then local results
                val combinedResults = (remotePodcasts + remoteEpisodes + local).distinctBy { it.episode.id }

                Logger.i("SearchController") { "合并去重后: ${combinedResults.size} 条结果 (播客: ${remotePodcasts.size}, 单集: ${remoteEpisodes.size + local.size})" }

                _searchState.update { current ->
                    current.copy(
                        searchResults = combinedResults,
                        isSearching = false,
                        isSearchActive = true,
                        searchErrorMessage = null,
                        searchOffset = local.size,  // Only count local results offset
                        hasMoreSearchResults = local.size >= limit,  // Only check if local has more
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _searchState.update { current ->
                    current.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        isSearchActive = true,
                        searchErrorMessage = throwable.message ?: "搜索失败，请稍后重试。",
                    )
                }
            }
        }
    }

    /**
     * Load more search results (pagination).
     */
    fun loadMoreSearchResults() {
        val currentState = _searchState.value

        // Return early if already loading or no more results
        if (currentState.isLoadingMoreResults || !currentState.hasMoreSearchResults) {
            return
        }

        val query = currentState.searchQuery.trim()
        if (query.isEmpty()) {
            return
        }

        _searchState.update { it.copy(isLoadingMoreResults = true) }

        scope.launch {
            try {
                val limit = currentState.searchLimit
                val offset = currentState.searchOffset
                val moreResults = repository.searchEpisodes(query, limit = limit, offset = offset)

                _searchState.update { current ->
                    current.copy(
                        searchResults = current.searchResults + moreResults,
                        searchOffset = current.searchOffset + moreResults.size,
                        hasMoreSearchResults = moreResults.size >= limit,
                        isLoadingMoreResults = false,
                    )
                }
            } catch (e: Exception) {
                _searchState.update { current ->
                    current.copy(
                        isLoadingMoreResults = false,
                        searchErrorMessage = e.message ?: "加载更多失败",
                    )
                }
            }
        }
    }

    /**
     * Clear search query and results.
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchState.update { current ->
            current.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearchActive = false,
                isSearching = false,
                searchErrorMessage = null,
                searchFilterType = SearchFilterType.ALL,
            )
        }
    }

    /**
     * Set search filter type.
     */
    fun setSearchFilterType(filterType: SearchFilterType) {
        _searchState.update { current ->
            current.copy(searchFilterType = filterType)
        }
    }

    private suspend fun searchApplePodcasts(query: String, limit: Int = 20): List<EpisodeWithPodcast> {
        Logger.d("SearchController") { "iTunes播客搜索开始: \"$query\", limit=$limit" }
        return try {
            val result = applePodcastSearchRepository.searchPodcast(query, limit = limit)
            val results = result.getOrNull()?.map { applePodcast ->
                // Convert ApplePodcast to EpisodeWithPodcast
                // Note: This creates a virtual Episode since iTunes API returns Podcast, not Episode
                val podcast = com.opoojkk.podium.data.model.Podcast(
                    id = "itunes_${applePodcast.collectionId}",
                    title = applePodcast.collectionName,
                    description = applePodcast.primaryGenreName ?: "",
                    artworkUrl = applePodcast.artworkUrl600 ?: applePodcast.artworkUrl100,
                    feedUrl = applePodcast.feedUrl,
                    lastUpdated = kotlinx.datetime.Clock.System.now(),
                    autoDownload = false,
                )

                // Create a virtual episode representing this podcast
                val episode = Episode(
                    id = "itunes_ep_${applePodcast.collectionId}",
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    title = applePodcast.collectionName,
                    description = "来自 iTunes: ${applePodcast.primaryGenreName ?: ""} · ${applePodcast.trackCount ?: 0} 集",
                    audioUrl = "",  // iTunes search results have no episode audio
                    publishDate = kotlinx.datetime.Clock.System.now(),
                    duration = null,
                    imageUrl = applePodcast.artworkUrl600 ?: applePodcast.artworkUrl100,
                    chapters = emptyList(),
                )

                EpisodeWithPodcast(episode = episode, podcast = podcast)
            } ?: emptyList()

            Logger.d("SearchController") { "🍎 iTunes播客搜索完成: 找到 ${results.size} 个结果" }
            results
        } catch (e: CancellationException) {
            Logger.w("SearchController") { "⏸️ iTunes播客搜索被取消" }
            throw e  // Re-throw cancellation exception
        } catch (e: Exception) {
            Logger.e("SearchController", "❌ iTunes播客搜索失败: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun searchAppleEpisodes(query: String, limit: Int = 20): List<EpisodeWithPodcast> {
        Logger.d("SearchController") { "iTunes单集搜索开始: \"$query\", limit=$limit" }
        return try {
            val result = applePodcastSearchRepository.searchEpisodes(query, limit = limit)
            val results = result.getOrNull()?.map { appleEpisode ->
                // Convert ApplePodcastEpisodeResult to EpisodeWithPodcast
                val podcast = com.opoojkk.podium.data.model.Podcast(
                    id = "itunes_${appleEpisode.collectionId}",
                    title = appleEpisode.collectionName,
                    description = appleEpisode.artistName ?: "",
                    artworkUrl = appleEpisode.artworkUrl600 ?: appleEpisode.artworkUrl100,
                    feedUrl = appleEpisode.feedUrl ?: "",
                    lastUpdated = kotlinx.datetime.Clock.System.now(),
                    autoDownload = false,
                )

                // Create actual episode
                val episode = Episode(
                    id = "itunes_ep_${appleEpisode.trackId}",
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    title = appleEpisode.trackName,
                    description = appleEpisode.description ?: "来自 iTunes",
                    audioUrl = appleEpisode.audioUrl ?: "",  // iTunes episode search may have audio URL
                    publishDate = try {
                        kotlinx.datetime.Instant.parse(appleEpisode.releaseDate)
                    } catch (e: Exception) {
                        kotlinx.datetime.Clock.System.now()
                    },
                    duration = appleEpisode.durationMs,
                    imageUrl = appleEpisode.artworkUrl600 ?: appleEpisode.artworkUrl100,
                    chapters = emptyList(),
                )

                EpisodeWithPodcast(episode = episode, podcast = podcast)
            } ?: emptyList()

            Logger.d("SearchController") { "🎧 iTunes单集搜索完成: 找到 ${results.size} 个结果" }
            results
        } catch (e: CancellationException) {
            Logger.w("SearchController") { "⏸️ iTunes单集搜索被取消" }
            throw e  // Re-throw cancellation exception
        } catch (e: Exception) {
            Logger.e("SearchController", "❌ iTunes单集搜索失败: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
