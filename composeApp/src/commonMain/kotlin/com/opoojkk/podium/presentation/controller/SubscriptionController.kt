package com.opoojkk.podium.presentation.controller

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.player.PodcastPlayer
import com.opoojkk.podium.util.ErrorHandler
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State for subscription management.
 */
data class SubscriptionState(
    val subscriptions: List<Podcast> = emptyList(),
    val isRefreshing: Boolean = false,
    val isAdding: Boolean = false,
    val errorMessage: String? = null,
    val duplicateSubscriptionTitle: String? = null,
)

/**
 * Controller responsible for subscription-related operations including
 * subscribe, unsubscribe, refresh, and auto-download management.
 */
class SubscriptionController(
    private val repository: PodcastRepository,
    private val downloadManager: PodcastDownloadManager,
    private val player: PodcastPlayer,
    private val scope: CoroutineScope,
) {
    private val _subscriptionState = MutableStateFlow(SubscriptionState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private var refreshJob: Job? = null

    // Cache for download episode lookups
    private val downloadEpisodeCache = mutableMapOf<String, com.opoojkk.podium.data.model.EpisodeWithPodcast?>()
    private val downloadEpisodeCacheMutex = Mutex()

    init {
        // Observe subscriptions from repository
        scope.launch {
            repository.observeSubscriptions().collect { podcasts ->
                _subscriptionState.value = _subscriptionState.value.copy(
                    subscriptions = podcasts,
                    isRefreshing = false,
                )
            }
        }

        // Check if auto-update is needed
        scope.launch {
            if (repository.shouldAutoUpdate()) {
                Logger.d("SubscriptionController") { "🔄 Auto-updating podcasts based on user settings" }
                refreshSubscriptions()
            } else {
                Logger.w("SubscriptionController") { "⏸️ Skipping auto-update (user preference or too soon)" }
            }
        }
    }

    /**
     * Refresh all subscriptions.
     */
    fun refreshSubscriptions(onComplete: ((Int) -> Unit)? = null) {
        if (refreshJob?.isActive == true) return
        _subscriptionState.value = _subscriptionState.value.copy(isRefreshing = true)
        refreshJob = scope.launch {
            val newEpisodesByPodcast = repository.refreshSubscriptions()

            // Calculate total new episodes
            val totalNewEpisodes = newEpisodesByPodcast.values.sumOf { it.size }

            // Auto-download new episodes for podcasts with auto-download enabled
            val podcasts = repository.observeSubscriptions().first()
            newEpisodesByPodcast.forEach { (podcastId, newEpisodes) ->
                val podcast = podcasts.find { it.id == podcastId }
                if (podcast?.autoDownload == true) {
                    newEpisodes.forEach { episode ->
                        downloadManager.enqueue(episode, auto = true)
                    }
                }
            }

            _subscriptionState.value = _subscriptionState.value.copy(isRefreshing = false)

            // Notify completion
            onComplete?.invoke(totalNewEpisodes)
        }
    }

    /**
     * Refresh a single podcast subscription.
     */
    fun refreshPodcast(podcastId: String, onComplete: (Int) -> Unit = {}) {
        scope.launch {
            val newEpisodes = repository.refreshPodcast(podcastId)

            // Auto-download new episodes if enabled
            val podcasts = repository.observeSubscriptions().first()
            val podcast = podcasts.find { it.id == podcastId }
            if (podcast?.autoDownload == true && newEpisodes.isNotEmpty()) {
                newEpisodes.forEach { episode ->
                    downloadManager.enqueue(episode, auto = true)
                }
            }

            onComplete(newEpisodes.size)
        }
    }

    /**
     * Subscribe to a podcast by feed URL.
     */
    fun subscribe(feedUrl: String) {
        scope.launch {
            // Set adding state
            _subscriptionState.value = _subscriptionState.value.copy(isAdding = true)

            try {
                Logger.d("SubscriptionController") { "🎧 Starting subscription process for: $feedUrl" }
                val result = repository.subscribe(feedUrl)
                Logger.d("SubscriptionController") { "🎧 Subscription completed, got ${result.episodes.size} episodes" }

                // Auto-download episodes if enabled
                if (result.podcast.autoDownload) {
                    result.episodes.forEach { episode ->
                        downloadManager.enqueue(episode, auto = true)
                    }
                }

                repository.setAutoDownload(result.podcast.id, result.podcast.autoDownload)
                Logger.d("SubscriptionController") { "🎧 Subscription process finished successfully" }
            } catch (e: com.opoojkk.podium.data.repository.DuplicateSubscriptionException) {
                // Handle duplicate subscription
                Logger.w("SubscriptionController") { "⚠️ Duplicate subscription detected: ${e.podcastTitle}" }
                _subscriptionState.value = _subscriptionState.value.copy(
                    duplicateSubscriptionTitle = e.podcastTitle
                )
            } catch (e: Exception) {
                val userError = ErrorHandler.logAndHandle("SubscriptionController", e)
                _subscriptionState.value = _subscriptionState.value.copy(
                    errorMessage = userError.message
                )
            } finally {
                // Clear loading state
                _subscriptionState.value = _subscriptionState.value.copy(isAdding = false)
            }
        }
    }

    /**
     * Clear duplicate subscription message.
     */
    fun clearDuplicateSubscriptionMessage() {
        _subscriptionState.value = _subscriptionState.value.copy(duplicateSubscriptionTitle = null)
    }

    /**
     * Delete a subscription by podcast ID.
     */
    fun deleteSubscription(podcastId: String) {
        scope.launch {
            val episodeIds = repository.getEpisodeIdsForPodcast(podcastId)
            repository.deleteSubscription(podcastId)
            if (episodeIds.isNotEmpty()) {
                downloadManager.clearDownloads(episodeIds)
                downloadEpisodeCacheMutex.withLock {
                    episodeIds.forEach { downloadEpisodeCache.remove(it) }
                }
            }
            val currentEpisode = player.state.value.episode
            if (currentEpisode?.podcastId == podcastId) {
                player.stop()
            }
        }
    }

    /**
     * Check if subscribed to a feed URL.
     */
    suspend fun checkIfSubscribed(feedUrl: String): Boolean {
        return repository.getPodcastByFeedUrl(feedUrl) != null
    }

    /**
     * Unsubscribe by feed URL.
     */
    fun unsubscribeByFeedUrl(feedUrl: String) {
        scope.launch {
            val podcast = repository.getPodcastByFeedUrl(feedUrl)
            if (podcast != null) {
                deleteSubscription(podcast.id)
            }
        }
    }

    /**
     * Rename a subscription.
     */
    fun renameSubscription(podcastId: String, newTitle: String) {
        scope.launch {
            repository.renameSubscription(podcastId, newTitle)
        }
    }

    /**
     * Toggle auto-download for all subscriptions.
     */
    fun toggleAutoDownload(enabled: Boolean) {
        downloadManager.setAutoDownload(enabled)
        scope.launch {
            _subscriptionState.value.subscriptions.forEach { podcast ->
                repository.setAutoDownload(podcast.id, enabled)
            }
        }
    }

    /**
     * Toggle auto-download for a specific podcast.
     */
    fun togglePodcastAutoDownload(podcastId: String, enabled: Boolean) {
        scope.launch {
            repository.setAutoDownload(podcastId, enabled)
            // Auto-download all episodes if enabled
            if (enabled) {
                val episodes = repository.observePodcastEpisodes(podcastId).first()
                episodes.forEach { episodeWithPodcast ->
                    downloadManager.enqueue(episodeWithPodcast.episode, auto = true)
                }
            }
        }
    }

    /**
     * Import subscriptions from OPML.
     */
    suspend fun importOpml(opml: String): PodcastRepository.OpmlImportResult =
        repository.importOpml(opml)

    /**
     * Import subscriptions from content.
     */
    suspend fun importSubscriptions(content: String): PodcastRepository.OpmlImportResult =
        repository.importSubscriptions(content)

    /**
     * Export subscriptions as OPML.
     */
    suspend fun exportOpml(): String = repository.exportOpml()

    /**
     * Export subscriptions in specified format.
     */
    suspend fun exportSubscriptions(format: PodcastRepository.ExportFormat): String =
        repository.exportSubscriptions(format)

    /**
     * Get episode details for download (with caching).
     */
    suspend fun getEpisodeDetailsForDownload(episodeId: String): com.opoojkk.podium.data.model.EpisodeWithPodcast? {
        val cached = downloadEpisodeCacheMutex.withLock {
            downloadEpisodeCache[episodeId]
        }
        if (cached != null) return cached

        val fetched = repository.getEpisodeWithPodcast(episodeId)
        downloadEpisodeCacheMutex.withLock {
            if (fetched == null) {
                downloadEpisodeCache.remove(episodeId)
            } else {
                downloadEpisodeCache[episodeId] = fetched
            }
        }
        return fetched
    }

    /**
     * Enqueue an episode for download.
     */
    fun enqueueDownload(episode: Episode) {
        downloadManager.enqueue(episode)
    }

    /**
     * Cancel a download.
     */
    fun cancelDownload(episodeId: String) {
        downloadManager.cancel(episodeId)
    }
}
