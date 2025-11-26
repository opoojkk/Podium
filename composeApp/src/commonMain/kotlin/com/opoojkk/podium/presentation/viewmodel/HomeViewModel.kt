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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
    val errorMessage: String? = null,
    val sectionErrors: Map<HomeSection, String> = emptyMap(),
    val lastUpdated: Instant? = null,
    val isFromCache: Boolean = false,
)

enum class HomeSection {
    Categories,
    HotEpisodes,
    HotPodcasts,
    NewEpisodes,
    NewPodcasts,
}

/**
 * ViewModel for home screen that manages categories and XYZRank data.
 * Handles parallel loading of all home screen data and podcast/episode interactions.
 */
class HomeViewModel(
    private val recommendedPodcastRepository: RecommendedPodcastRepository,
    private val xyzRankRepository: XYZRankRepository,
    private val applePodcastSearchRepository: ApplePodcastSearchRepository,
    private val homeCache: HomeCache,
    private val scope: CoroutineScope
) {
    private val cacheTtl: Duration = 10.minutes
    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        loadAllData()
    }

    /**
     * Load all home screen data in parallel.
     */
    fun loadAllData(forceRefresh: Boolean = false) {
        scope.launch {
            val cachedPayload = homeCache.load()
            val cachedViewState = cachedPayload?.toViewState(isLoading = false, fromCache = true)
            if (!forceRefresh && cachedPayload != null) {
                val fresh = cachedPayload.isFresh(cacheTtl.inWholeMilliseconds)
                _state.value = cachedPayload.toViewState(isLoading = !fresh, fromCache = true)
                if (fresh) {
                    Logger.d("HomeViewModel") { "Using fresh cached home data" }
                    return@launch
                }
                Logger.d("HomeViewModel") { "Cached data found but stale, refreshing..." }
            } else {
                _state.update { it.copy(isLoading = true, errorMessage = null, sectionErrors = emptyMap()) }
            }

            coroutineScope {
                Logger.d("HomeViewModel") { "Loading all data in parallel..." }
                val sectionErrors = mutableMapOf<HomeSection, String>()

                val categoriesDeferred = async {
                    runCatching {
                        recommendedPodcastRepository.getAllCategories().getOrThrow()
                    }.getOrElse { error ->
                        sectionErrors[HomeSection.Categories] = error.message ?: "分类加载失败"
                        emptyList()
                    }
                }

                val hotEpisodesDeferred = async {
                    runCatching {
                        xyzRankRepository.getHotEpisodes().getOrThrow()
                            .take(10)
                            .map { it.toEpisodeWithPodcast() }
                            .distinctBy { it.episode.id }
                    }.getOrElse { error ->
                        sectionErrors[HomeSection.HotEpisodes] = error.message ?: "热门单集加载失败"
                        emptyList()
                    }
                }

                val hotPodcastsDeferred = async {
                    runCatching {
                        xyzRankRepository.getHotPodcasts().getOrThrow()
                            .take(10)
                            .map { it.toPodcast() }
                            .distinctBy { it.id }
                    }.getOrElse { error ->
                        sectionErrors[HomeSection.HotPodcasts] = error.message ?: "热门播客加载失败"
                        emptyList()
                    }
                }

                val newEpisodesDeferred = async {
                    runCatching {
                        xyzRankRepository.getNewEpisodes().getOrThrow()
                            .take(10)
                            .map { it.toEpisodeWithPodcast() }
                            .distinctBy { it.episode.id }
                    }.getOrElse { error ->
                        sectionErrors[HomeSection.NewEpisodes] = error.message ?: "最新单集加载失败"
                        emptyList()
                    }
                }

                val newPodcastsDeferred = async {
                    runCatching {
                        xyzRankRepository.getNewPodcasts().getOrThrow()
                            .take(10)
                            .map { it.toPodcast() }
                            .distinctBy { it.id }
                    }.getOrElse { error ->
                        sectionErrors[HomeSection.NewPodcasts] = error.message ?: "最新播客加载失败"
                        emptyList()
                    }
                }

                var usedCacheFallback = false

                fun <T> fallbackIfCached(
                    section: HomeSection,
                    fresh: List<T>,
                    cached: List<T>?
                ): List<T> {
                    if (sectionErrors.containsKey(section)) {
                        cached?.takeIf { it.isNotEmpty() }?.let {
                            sectionErrors.remove(section)
                            usedCacheFallback = true
                            return it
                        }
                    }
                    return fresh
                }

                val categories = fallbackIfCached(
                    HomeSection.Categories,
                    categoriesDeferred.await(),
                    cachedViewState?.categories
                )

                val hotEpisodes = fallbackIfCached(
                    HomeSection.HotEpisodes,
                    hotEpisodesDeferred.await(),
                    cachedViewState?.hotEpisodes
                )

                val hotPodcasts = fallbackIfCached(
                    HomeSection.HotPodcasts,
                    hotPodcastsDeferred.await(),
                    cachedViewState?.hotPodcasts
                )

                val newEpisodes = fallbackIfCached(
                    HomeSection.NewEpisodes,
                    newEpisodesDeferred.await(),
                    cachedViewState?.newEpisodes
                )

                val newPodcasts = fallbackIfCached(
                    HomeSection.NewPodcasts,
                    newPodcastsDeferred.await(),
                    cachedViewState?.newPodcasts
                )

                val newState = HomeViewState(
                    categories = categories,
                    hotEpisodes = hotEpisodes,
                    hotPodcasts = hotPodcasts,
                    newEpisodes = newEpisodes,
                    newPodcasts = newPodcasts,
                    isLoading = false,
                    errorMessage = if (sectionErrors.size == HomeSection.entries.size) "全部数据加载失败" else null,
                    sectionErrors = sectionErrors,
                    lastUpdated = if (usedCacheFallback && cachedPayload != null) {
                        Instant.fromEpochMilliseconds(cachedPayload.cachedAtEpochMs)
                    } else {
                        Clock.System.now()
                    },
                    isFromCache = usedCacheFallback,
                )

                _state.value = newState
                homeCache.save(newState)

                Logger.i("HomeViewModel") {
                    "Loaded data - Categories: ${newState.categories.size}, " +
                            "Hot Episodes: ${newState.hotEpisodes.size}, " +
                            "Hot Podcasts: ${newState.hotPodcasts.size}, " +
                            "New Episodes: ${newState.newEpisodes.size}, " +
                            "New Podcasts: ${newState.newPodcasts.size}"
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

            result.getOrNull()
                ?.sortedByDescending { scoreEpisodeMatch(it, episode) }
                ?.firstOrNull()
                ?.let { found ->
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

            result.getOrNull()
                ?.sortedByDescending { scorePodcastMatch(it, podcast) }
                ?.firstOrNull()
                ?.let { found ->
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

    private fun scoreEpisodeMatch(candidate: com.opoojkk.podium.data.repository.ApplePodcastEpisodeResult, target: Episode): Int {
        var score = 0
        val normalizedTitle = target.title.normalize()
        val candidateTitle = candidate.trackName.normalize()
        if (candidateTitle == normalizedTitle) score += 5
        if (candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle)) score += 3

        val normalizedPodcast = target.podcastTitle.normalize()
        val candidatePodcast = candidate.collectionName.normalize()
        if (candidatePodcast == normalizedPodcast) score += 5 else if (candidatePodcast.contains(normalizedPodcast)) score += 2

        candidate.durationMs?.let { duration ->
            target.duration?.let { original ->
                if (abs(duration - original) < 30_000) score += 2
            }
        }

        return score
    }

    private fun scorePodcastMatch(candidate: com.opoojkk.podium.data.repository.ApplePodcastResult, target: Podcast): Int {
        val candidateName = candidate.collectionName.normalize()
        val targetName = target.title.normalize()
        var score = if (candidateName == targetName) 6 else 0
        if (candidateName.contains(targetName) || targetName.contains(candidateName)) score += 3
        target.description.takeIf { it.isNotBlank() }?.let { desc ->
            if (candidate.genres?.any { genre -> desc.contains(genre, ignoreCase = true) } == true) {
                score += 1
            }
        }
        return score
    }

    private fun String.normalize(): String = trim().lowercase()
}
