package com.opoojkk.podium.presentation

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.platform.fileLastModifiedMillis
import com.opoojkk.podium.platform.fileSizeInBytes
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val downloadEpisodeCache = mutableMapOf<String, EpisodeWithPodcast?>()
    private val downloadEpisodeCacheMutex = Mutex()

    val playbackState: StateFlow<com.opoojkk.podium.data.model.PlaybackState> = player.state

    // 查看更多页面使用的完整列表
    val allRecentListening = repository.observeAllRecentListening()
    val allRecentUpdates = repository.observeAllRecentUpdates()

    // 获取特定播客的所有单集
    fun getPodcastEpisodes(podcastId: String) = repository.observePodcastEpisodes(podcastId)

    private var refreshJob: Job? = null
    private var playbackSaveJob: Job? = null

    init {
        // 加载上次播放的单集
        scope.launch {
            val lastPlayed = repository.getLastPlayedEpisode()
            if (lastPlayed != null) {
                val (episode, progress) = lastPlayed
                println("🎵 PodiumController: Restoring last played episode: ${episode.title} at ${progress.positionMs}ms")
                player.restorePlaybackState(episode, progress.positionMs)
            }
        }

        // 监听播放状态变化，定期保存进度
        scope.launch {
            player.state.collect { state ->
                if (state.episode != null && state.isPlaying) {
                    // 启动定期保存任务
                    if (playbackSaveJob?.isActive != true) {
                        playbackSaveJob = scope.launch {
                            while (state.isPlaying) {
                                kotlinx.coroutines.delay(10_000) // 每10秒保存一次
                                val currentState = player.state.value
                                if (currentState.episode != null) {
                                    repository.savePlayback(
                                        PlaybackProgress(
                                            episodeId = currentState.episode.id,
                                            positionMs = currentState.positionMs,
                                            durationMs = currentState.durationMs ?: currentState.episode.duration,
                                            updatedAt = Clock.System.now(),
                                        ),
                                    )
                                    println("🎵 PodiumController: Auto-saved playback progress: ${currentState.positionMs}ms")
                                }
                            }
                        }
                    }
                } else {
                    // 停止播放时取消定期保存任务
                    playbackSaveJob?.cancel()
                    playbackSaveJob = null
                }
            }
        }

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

                            val details = episodeDetailsForDownload(status.episodeId)
                            if (details != null) {
                                val completedAt = status.filePath.takeIf { it.isNotBlank() }?.let { fileLastModifiedMillis(it) }
                                cachedItems += ProfileCachedItem(
                                    episodeId = details.episode.id,
                                    episodeTitle = details.episode.title,
                                    podcastTitle = details.podcast.title,
                                    sizeBytes = resolvedSize,
                                    filePath = status.filePath.takeIf { it.isNotBlank() },
                                    completedAtMillis = completedAt,
                                )
                            }
                        }

                        is DownloadStatus.InProgress -> {
                            val details = episodeDetailsForDownload(status.episodeId) ?: continue
                            inProgressItems += ProfileDownloadItem(
                                episodeId = details.episode.id,
                                episodeTitle = details.episode.title,
                                podcastTitle = details.podcast.title,
                                status = status,
                            )
                        }

                        is DownloadStatus.Idle, is DownloadStatus.Failed -> {
                            val details = episodeDetailsForDownload(status.episodeId) ?: continue
                            queuedItems += ProfileDownloadItem(
                                episodeId = details.episode.id,
                                episodeTitle = details.episode.title,
                                podcastTitle = details.podcast.title,
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
                    durationMs = episode.duration,
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
            val newEpisodesByPodcast = repository.refreshSubscriptions()
            
            // 对于启用自动下载的播客，下载新节目
            val podcasts = repository.observeSubscriptions().first()
            newEpisodesByPodcast.forEach { (podcastId, newEpisodes) ->
                val podcast = podcasts.find { it.id == podcastId }
                if (podcast?.autoDownload == true) {
                    newEpisodes.forEach { episode ->
                        downloadManager.enqueue(episode, auto = true)
                    }
                }
            }
            
            _subscriptionsState.value = _subscriptionsState.value.copy(isRefreshing = false)
        }
    }

    fun subscribe(feedUrl: String) {
        scope.launch {
            try {
                println("🎧 Controller: Starting subscription process for: $feedUrl")
                val result = repository.subscribe(feedUrl)
                println("🎧 Controller: Subscription completed, got ${result.episodes.size} episodes")
                
                // 如果启用自动下载，下载该播客的所有节目
                if (result.podcast.autoDownload) {
                    result.episodes.forEach { episode ->
                        downloadManager.enqueue(episode, auto = true)
                    }
                }
                
                repository.setAutoDownload(result.podcast.id, result.podcast.autoDownload)
                println("🎧 Controller: Subscription process finished successfully")
            } catch (e: com.opoojkk.podium.data.repository.DuplicateSubscriptionException) {
                // 捕获重复订阅异常，显示提示
                println("⚠️ Controller: Duplicate subscription detected: ${e.podcastTitle}")
                println("⚠️ Controller: Setting duplicateSubscriptionTitle in state")
                _subscriptionsState.value = _subscriptionsState.value.copy(
                    duplicateSubscriptionTitle = e.podcastTitle
                )
                println("⚠️ Controller: State updated, duplicateSubscriptionTitle = ${_subscriptionsState.value.duplicateSubscriptionTitle}")
            } catch (e: Exception) {
                println("❌ Controller: Subscription failed: ${e.message}")
                println("❌ Controller: Exception type: ${e::class.simpleName}")
                e.printStackTrace()
            }
        }
    }

    fun clearDuplicateSubscriptionMessage() {
        _subscriptionsState.value = _subscriptionsState.value.copy(duplicateSubscriptionTitle = null)
    }

    fun toggleAutoDownload(enabled: Boolean) {
        downloadManager.setAutoDownload(enabled)
        scope.launch {
            _profileState.value.subscribedPodcasts.forEach { podcast ->
                repository.setAutoDownload(podcast.id, enabled)
            }
        }
    }

    fun togglePodcastAutoDownload(podcastId: String, enabled: Boolean) {
        scope.launch {
            repository.setAutoDownload(podcastId, enabled)
            // 如果启用自动缓存，下载该播客的所有节目
            if (enabled) {
                val episodes = repository.observePodcastEpisodes(podcastId).first()
                episodes.forEach { episodeWithPodcast ->
                    downloadManager.enqueue(episodeWithPodcast.episode, auto = true)
                }
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

    private suspend fun episodeDetailsForDownload(episodeId: String): EpisodeWithPodcast? {
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

    fun renameSubscription(podcastId: String, newTitle: String) {
        scope.launch {
            repository.renameSubscription(podcastId, newTitle)
        }
    }
}
