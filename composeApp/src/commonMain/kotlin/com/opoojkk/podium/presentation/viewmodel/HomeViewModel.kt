package com.opoojkk.podium.presentation.viewmodel

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.model.recommended.PodcastCategory
import com.opoojkk.podium.data.model.recommended.RecommendedPodcast
import com.opoojkk.podium.data.mapper.toEpisodeWithPodcast
import com.opoojkk.podium.data.mapper.toPodcast
import com.opoojkk.podium.data.repository.ApplePodcastSearchRepository
import com.opoojkk.podium.data.repository.RecommendedPodcastRepository
import com.opoojkk.podium.data.repository.XYZRankRepository
import com.opoojkk.podium.util.ErrorHandler
import com.opoojkk.podium.util.Logger
import com.opoojkk.podium.util.tryOrDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for home screen data including categories and XYZRank content.
 */
data class HomeViewState(
    val categories: List<PodcastCategory> = emptyList(),
    val hotEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val hotPodcasts: List<Podcast> = emptyList(),
    val newEpisodes: List<EpisodeWithPodcast> = emptyList(),
    val newPodcasts: List<Podcast> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for home screen that manages categories and XYZRank data.
 * Handles parallel loading of all home screen data and podcast/episode interactions.
 */
class HomeViewModel(
    private val recommendedPodcastRepository: RecommendedPodcastRepository,
    private val xyzRankRepository: XYZRankRepository,
    private val applePodcastSearchRepository: ApplePodcastSearchRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        loadAllData()
    }

    /**
     * Load all home screen data in parallel.
     */
    fun loadAllData() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            Logger.d("HomeViewModel") { "Loading all data in parallel..." }

            coroutineScope {
                // Execute all network requests in parallel
                val categoriesDeferred = async {
                    tryOrDefault("HomeViewModel", emptyList()) {
                        recommendedPodcastRepository.getAllCategories().getOrNull() ?: emptyList()
                    }
                }

                val hotEpisodesDeferred = async {
                    tryOrDefault("HomeViewModel", emptyList()) {
                        xyzRankRepository.getHotEpisodes().getOrNull()
                            ?.take(10)
                            ?.map { it.toEpisodeWithPodcast() }
                            ?: emptyList()
                    }
                }

                val hotPodcastsDeferred = async {
                    tryOrDefault("HomeViewModel", emptyList()) {
                        xyzRankRepository.getHotPodcasts().getOrNull()
                            ?.take(10)
                            ?.map { it.toPodcast() }
                            ?: emptyList()
                    }
                }

                val newEpisodesDeferred = async {
                    tryOrDefault("HomeViewModel", emptyList()) {
                        xyzRankRepository.getNewEpisodes().getOrNull()
                            ?.take(10)
                            ?.map { it.toEpisodeWithPodcast() }
                            ?: emptyList()
                    }
                }

                val newPodcastsDeferred = async {
                    tryOrDefault("HomeViewModel", emptyList()) {
                        xyzRankRepository.getNewPodcasts().getOrNull()
                            ?.take(10)
                            ?.map { it.toPodcast() }
                            ?: emptyList()
                    }
                }

                // Wait for all requests and update state
                _state.update {
                    it.copy(
                        categories = categoriesDeferred.await(),
                        hotEpisodes = hotEpisodesDeferred.await(),
                        hotPodcasts = hotPodcastsDeferred.await(),
                        newEpisodes = newEpisodesDeferred.await(),
                        newPodcasts = newPodcastsDeferred.await(),
                        isLoading = false
                    )
                }

                Logger.i("HomeViewModel") {
                    "Loaded data - Categories: ${_state.value.categories.size}, " +
                            "Hot Episodes: ${_state.value.hotEpisodes.size}, " +
                            "Hot Podcasts: ${_state.value.hotPodcasts.size}, " +
                            "New Episodes: ${_state.value.newEpisodes.size}, " +
                            "New Podcasts: ${_state.value.newPodcasts.size}"
                }
            }
        }
    }

    /**
     * Search for an XYZRank episode on Apple Podcasts and return a playable episode.
     * Returns null if not found or if audio URL is not available.
     */
    suspend fun searchAndConvertXYZRankEpisode(episode: Episode): Episode? {
        if (!episode.id.startsWith("xyzrank_episode_")) {
            return episode // Not an XYZRank episode
        }

        Logger.d("HomeViewModel") { "Searching Apple Podcast for episode: ${episode.title}" }

        return try {
            val result = applePodcastSearchRepository.searchEpisode(
                podcastName = episode.podcastTitle,
                episodeTitle = episode.title
            )

            result.getOrNull()?.firstOrNull()?.let { found ->
                val validAudioUrl = found.audioUrl?.takeIf { it.isNotBlank() } ?: return null

                val publishDate = try {
                    kotlinx.datetime.Instant.parse(found.releaseDate)
                } catch (e: Exception) {
                    Logger.w("HomeViewModel") { "Failed to parse date: ${found.releaseDate}" }
                    kotlinx.datetime.Clock.System.now()
                }

                Episode(
                    id = "apple_${found.trackId}",
                    podcastId = found.collectionId.toString(),
                    podcastTitle = found.collectionName,
                    title = found.trackName,
                    description = found.description ?: episode.description,
                    audioUrl = validAudioUrl,
                    publishDate = publishDate,
                    duration = found.durationMs,
                    imageUrl = found.artworkUrl600 ?: found.artworkUrl100 ?: episode.imageUrl
                )
            }
        } catch (e: Exception) {
            ErrorHandler.logAndHandle("HomeViewModel", e)
            null
        }
    }

    /**
     * Search for an XYZRank podcast on Apple Podcasts and convert to RecommendedPodcast.
     * Returns null if not found.
     */
    suspend fun searchAndConvertXYZRankPodcast(podcast: Podcast): RecommendedPodcast? {
        if (!podcast.id.startsWith("xyzrank_podcast_")) {
            return null // Not an XYZRank podcast
        }

        Logger.d("HomeViewModel") { "Searching Apple Podcast for: ${podcast.title}" }

        return try {
            val result = applePodcastSearchRepository.searchPodcast(
                query = podcast.title,
                limit = 5
            )

            result.getOrNull()?.firstOrNull()?.let { found ->
                RecommendedPodcast(
                    id = found.collectionId.toString(),
                    name = found.collectionName,
                    host = found.artistName,
                    description = podcast.description,
                    artworkUrl = found.artworkUrl600 ?: found.artworkUrl100,
                    rssUrl = found.feedUrl
                )
            }
        } catch (e: Exception) {
            ErrorHandler.logAndHandle("HomeViewModel", e)
            null
        }
    }

    /**
     * Extract web link from podcast description (for 小宇宙 fallback).
     */
    fun extractWebLink(description: String): String? {
        val linkMatch = Regex("链接：(https?://[^\\s]+)").find(description)
        return linkMatch?.groupValues?.get(1)
    }
}
