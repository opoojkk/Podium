package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.SleepTimerDuration
import com.opoojkk.podium.data.model.SleepTimerState
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.platform.fileLastModifiedMillis
import com.opoojkk.podium.platform.fileSizeInBytes
import com.opoojkk.podium.player.PodcastPlayer
import com.opoojkk.podium.presentation.controller.PlaybackController
import com.opoojkk.podium.presentation.controller.PlaylistController
import com.opoojkk.podium.presentation.controller.SearchController
import com.opoojkk.podium.presentation.controller.SubscriptionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Main coordinator controller that orchestrates all sub-controllers.
 * This controller follows the Single Responsibility Principle by delegating
 * specific responsibilities to specialized controllers:
 * - PlaybackController: Playback operations and sleep timer
 * - SearchController: Search functionality
 * - SubscriptionController: Subscription management
 * - PlaylistController: Playlist management
 */
class PodiumController(
    private val repository: PodcastRepository,
    private val applePodcastSearchRepository: com.opoojkk.podium.data.repository.ApplePodcastSearchRepository,
    private val player: PodcastPlayer,
    private val downloadManager: PodcastDownloadManager,
    private val scope: CoroutineScope,
) {
    // Sub-controllers
    private val playbackController = PlaybackController(repository, player, scope)
    private val searchController = SearchController(repository, applePodcastSearchRepository, scope)
    private val subscriptionController = SubscriptionController(repository, downloadManager, player, scope)
    private val playlistController = PlaylistController(repository, scope)

    // UI States
    private val _homeState = MutableStateFlow(HomeUiState(isLoading = true))
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileUiState(updateInterval = repository.getUpdateInterval()))
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

    // Delegated states from sub-controllers
    val subscriptionsState = subscriptionController.subscriptionState
    val playlistState = playlistController.playlistState
    val playbackState = playbackController.playbackState
    val sleepTimerState = playbackController.sleepTimerState

    // Search state (exposed via homeState for backward compatibility)
    private val searchState = searchController.searchState

    // Full lists for "see more" pages
    val allRecentListening = repository.observeAllRecentListening()
    val allRecentUpdates = repository.observeAllRecentUpdates()

    // Sleep timer callback
    var onSleepTimerComplete: (() -> Unit)?
        get() = playbackController.onSleepTimerComplete
        set(value) { playbackController.onSleepTimerComplete = value }

    init {
        // Set downloads state flow for playback controller
        playbackController.setDownloads(downloads)

        // Observe home state from repository
        scope.launch {
            repository.observeHomeState().collect { data ->
                _homeState.value = _homeState.value.copy(
                    recentPlayed = data.recentPlayed,
                    recentUpdates = data.recentUpdates,
                    isLoading = data.isLoading,
                    errorMessage = data.errorMessage,
                )
            }
        }

        // Sync search state to home state
        scope.launch {
            searchState.collect { search ->
                _homeState.value = _homeState.value.copy(
                    searchQuery = search.searchQuery,
                    searchResults = search.searchResults,
                    isSearchActive = search.isSearchActive,
                    isSearching = search.isSearching,
                    searchErrorMessage = search.searchErrorMessage,
                    searchFilterType = search.searchFilterType,
                    searchOffset = search.searchOffset,
                    searchLimit = search.searchLimit,
                    hasMoreSearchResults = search.hasMoreSearchResults,
                    isLoadingMoreResults = search.isLoadingMoreResults,
                )
            }
        }

        // Observe subscriptions for profile state
        scope.launch {
            subscriptionController.subscriptionState.collect { state ->
                _profileState.value = _profileState.value.copy(
                    subscribedPodcasts = state.subscriptions,
                )
            }
        }

        // Observe downloads and update profile state
        scope.launch {
            combine(
                repository.observeDownloads(),
                downloadManager.downloads,
            ) { persisted, runtime ->
                persisted + runtime
            }
                .debounce(300) // Debounce to avoid too frequent updates during downloads
                .distinctUntilChanged() // Only update when the map actually changes
                .collect { combined ->
                _downloads.value = combined
                val bytesPerMb = 1024L * 1024L
                val defaultEpisodeSizeBytes = 50L * bytesPerMb

                var totalCacheBytes = 0L
                val cachedItems = mutableListOf<ProfileCachedItem>()
                val inProgressItems = mutableListOf<ProfileDownloadItem>()
                val queuedItems = mutableListOf<ProfileDownloadItem>()

                for (status in combined.values) {
                    when (status) {
                        is DownloadStatus.Completed -> {
                            val sizeBytes = status.filePath.takeIf { it.isNotBlank() }?.let { fileSizeInBytes(it) }
                            val resolvedSize = sizeBytes ?: defaultEpisodeSizeBytes
                            totalCacheBytes += resolvedSize

                            val details = subscriptionController.getEpisodeDetailsForDownload(status.episodeId)
                            if (details != null) {
                                val completedAt = status.filePath.takeIf { it.isNotBlank() }?.let { fileLastModifiedMillis(it) }
                                cachedItems += ProfileCachedItem(
                                    episodeId = details.episode.id,
                                    episodeTitle = details.episode.title,
                                    podcastTitle = details.podcast.title,
                                    podcastArtworkUrl = details.podcast.artworkUrl,
                                    sizeBytes = resolvedSize,
                                    filePath = status.filePath.takeIf { it.isNotBlank() },
                                    completedAtMillis = completedAt,
                                )
                            }
                        }

                        is DownloadStatus.InProgress -> {
                            val details = subscriptionController.getEpisodeDetailsForDownload(status.episodeId) ?: continue
                            inProgressItems += ProfileDownloadItem(
                                episodeId = details.episode.id,
                                episodeTitle = details.episode.title,
                                podcastTitle = details.podcast.title,
                                podcastArtworkUrl = details.podcast.artworkUrl,
                                status = status,
                            )
                        }

                        is DownloadStatus.Idle, is DownloadStatus.Failed -> {
                            val details = subscriptionController.getEpisodeDetailsForDownload(status.episodeId) ?: continue
                            queuedItems += ProfileDownloadItem(
                                episodeId = details.episode.id,
                                episodeTitle = details.episode.title,
                                podcastTitle = details.podcast.title,
                                podcastArtworkUrl = details.podcast.artworkUrl,
                                status = status,
                            )
                        }
                    }
                }

                val cacheSizeInMb = if (totalCacheBytes == 0L) 0 else {
                    val roundedUp = (totalCacheBytes + bytesPerMb - 1) / bytesPerMb
                    roundedUp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }

                cachedItems.sortWith(
                    compareByDescending<ProfileCachedItem> { it.completedAtMillis ?: Long.MIN_VALUE }
                        .thenByDescending { it.sizeBytes }
                        .thenBy { it.episodeTitle.lowercase() }
                )
                inProgressItems.sortByDescending { (it.status as? DownloadStatus.InProgress)?.progress ?: 0f }
                queuedItems.sortBy { it.episodeTitle.lowercase() }

                _profileState.value = _profileState.value.copy(
                    cacheSizeInMb = cacheSizeInMb,
                    cachedDownloads = cachedItems.toList(),
                    inProgressDownloads = inProgressItems.toList(),
                    queuedDownloads = queuedItems.toList(),
                )
            }
        }
    }

    // ==================== Playback Methods ====================
    fun playEpisode(episode: Episode) = playbackController.playEpisode(episode)
    fun resume() = playbackController.resume()
    fun pause() = playbackController.pause()
    fun stop() = playbackController.stop()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = playbackController.seekBy(deltaMs)
    fun setPlaybackSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)
    fun startSleepTimer(duration: SleepTimerDuration) = playbackController.startSleepTimer(duration)
    fun cancelSleepTimer() = playbackController.cancelSleepTimer()

    // ==================== Search Methods ====================
    fun onHomeSearchQueryChange(query: String) = searchController.onSearchQueryChange(query)
    fun loadMoreSearchResults() = searchController.loadMoreSearchResults()
    fun clearHomeSearch() = searchController.clearSearch()
    fun setSearchFilterType(filterType: SearchFilterType) = searchController.setSearchFilterType(filterType)

    // ==================== Subscription Methods ====================
    fun refreshSubscriptions(onComplete: ((Int) -> Unit)? = null) = subscriptionController.refreshSubscriptions(onComplete)
    fun refreshPodcast(podcastId: String, onComplete: (Int) -> Unit = {}) = subscriptionController.refreshPodcast(podcastId, onComplete)
    fun subscribe(feedUrl: String) = subscriptionController.subscribe(feedUrl)
    fun clearDuplicateSubscriptionMessage() = subscriptionController.clearDuplicateSubscriptionMessage()
    fun toggleAutoDownload(enabled: Boolean) = subscriptionController.toggleAutoDownload(enabled)
    fun togglePodcastAutoDownload(podcastId: String, enabled: Boolean) = subscriptionController.togglePodcastAutoDownload(podcastId, enabled)
    fun enqueueDownload(episode: Episode) = subscriptionController.enqueueDownload(episode)
    fun cancelDownload(episodeId: String) = subscriptionController.cancelDownload(episodeId)
    suspend fun importOpml(opml: String): PodcastRepository.OpmlImportResult = subscriptionController.importOpml(opml)
    suspend fun importSubscriptions(content: String): PodcastRepository.OpmlImportResult = subscriptionController.importSubscriptions(content)
    suspend fun exportOpml(): String = subscriptionController.exportOpml()
    suspend fun exportSubscriptions(format: PodcastRepository.ExportFormat): String = subscriptionController.exportSubscriptions(format)
    fun deleteSubscription(podcastId: String) = subscriptionController.deleteSubscription(podcastId)
    suspend fun checkIfSubscribed(feedUrl: String): Boolean = subscriptionController.checkIfSubscribed(feedUrl)
    fun unsubscribeByFeedUrl(feedUrl: String) = subscriptionController.unsubscribeByFeedUrl(feedUrl)
    fun renameSubscription(podcastId: String, newTitle: String) = subscriptionController.renameSubscription(podcastId, newTitle)

    // ==================== Playlist Methods ====================
    fun markEpisodeCompleted(episodeId: String) = playlistController.markEpisodeCompleted(episodeId)
    fun removeFromPlaylist(episodeId: String) = playlistController.removeFromPlaylist(episodeId)
    fun addToPlaylist(episodeId: String) = playlistController.addToPlaylist(episodeId)

    // ==================== Other Methods ====================
    fun getPodcastEpisodes(podcastId: String) = repository.observePodcastEpisodes(podcastId)

    fun setUpdateInterval(interval: com.opoojkk.podium.data.model.UpdateInterval) {
        scope.launch {
            repository.setUpdateInterval(interval)
            _profileState.value = _profileState.value.copy(updateInterval = interval)
        }
    }
}
