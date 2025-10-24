package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class PodiumController(
    private val repository: PodcastRepository,
    private val player: PodcastPlayer,
    private val downloadManager: PodcastDownloadManager,
    private val scope: CoroutineScope,
) {

    private val _homeState = MutableStateFlow(HomeUiState(isLoading = true))
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _subscriptionsState = MutableStateFlow(SubscriptionsUiState())
    val subscriptionsState: StateFlow<SubscriptionsUiState> = _subscriptionsState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()

    val playbackState: StateFlow<com.opoojkk.podium.data.model.PlaybackState> = player.state

    private var refreshJob: Job? = null

    init {
        scope.launch {
            repository.observeHomeState().collect { homeState ->
                println("🏠 Home state updated: ${homeState.recentUpdates.size} recent updates, ${homeState.recentPlayed.size} recent played")
                _homeState.value = homeState
            }
        }
        scope.launch {
            repository.observeSubscriptions().collect { podcasts ->
                _subscriptionsState.value = _subscriptionsState.value.copy(
                    subscriptions = podcasts,
                    isRefreshing = false,
                )
                _profileState.value = _profileState.value.copy(
                    subscribedPodcasts = podcasts,
                )
            }
        }
        scope.launch {
            combine(
                repository.observeDownloads(),
                downloadManager.downloads,
            ) { persisted, runtime ->
                persisted + runtime
            }.collect { combined ->
                _downloads.value = combined
                val cachedCount = combined.values.count { it is DownloadStatus.Completed }
                _profileState.value = _profileState.value.copy(
                    cacheSizeInMb = cachedCount * 50,
                )
            }
        }
        scope.launch {
            downloadManager.autoDownloadEnabled.collect { enabled ->
                _profileState.value = _profileState.value.copy(autoDownload = enabled)
            }
        }
    }

    fun playEpisode(episode: Episode) {
        println("🎵 PodiumController: playEpisode called for: ${episode.title}")
        println("🎵 PodiumController: Audio URL: ${episode.audioUrl}")
        scope.launch {
            val progress = repository.playbackForEpisode(episode.id)
            val startPosition = progress?.positionMs ?: 0L
            println("🎵 PodiumController: Starting playback at position: $startPosition")
            player.play(episode, startPosition)
            repository.savePlayback(
                PlaybackProgress(
                    episodeId = episode.id,
                    positionMs = startPosition,
                    updatedAt = Clock.System.now(),
                ),
            )
        }
    }

    fun resume() = player.resume()

    fun pause() = player.pause()

    fun stop() = player.stop()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

    fun refreshSubscriptions() {
        if (refreshJob?.isActive == true) return
        _subscriptionsState.value = _subscriptionsState.value.copy(isRefreshing = true)
        refreshJob = scope.launch {
            repository.refreshSubscriptions()
            _subscriptionsState.value = _subscriptionsState.value.copy(isRefreshing = false)
        }
    }

    fun subscribe(feedUrl: String) {
        scope.launch {
            try {
                println("🎧 Controller: Starting subscription process for: $feedUrl")
                val result = repository.subscribe(feedUrl)
                println("🎧 Controller: Subscription completed, got ${result.episodes.size} episodes")
                if (downloadManager.isAutoDownloadEnabled()) {
                    result.episodes.maxByOrNull { it.publishDate }?.let { latest ->
                        downloadManager.enqueue(latest, auto = true)
                    }
                }
                repository.setAutoDownload(result.podcast.id, downloadManager.isAutoDownloadEnabled())
                println("🎧 Controller: Subscription process finished successfully")
            } catch (e: Exception) {
                println("❌ Controller: Subscription failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun toggleAutoDownload(enabled: Boolean) {
        downloadManager.setAutoDownload(enabled)
        scope.launch {
            _profileState.value.subscribedPodcasts.forEach { podcast ->
                repository.setAutoDownload(podcast.id, enabled)
            }
        }
    }

    fun enqueueDownload(episode: Episode) {
        downloadManager.enqueue(episode)
    }

    fun cancelDownload(episodeId: String) {
        downloadManager.cancel(episodeId)
    }

    suspend fun importOpml(opml: String) {
        repository.importOpml(opml)
    }

    suspend fun exportOpml(): String = repository.exportOpml()

    fun deleteSubscription(podcastId: String) {
        scope.launch {
            repository.deleteSubscription(podcastId)
        }
    }

    fun renameSubscription(podcastId: String, newTitle: String) {
        scope.launch {
            repository.renameSubscription(podcastId, newTitle)
        }
    }
}
