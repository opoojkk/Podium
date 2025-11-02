package com.opoojkk.podium.data.repository

import com.opoojkk.podium.data.local.PodcastDao
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.model.PlaylistItem
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.rss.PodcastFeedService
import com.opoojkk.podium.data.rss.RssEpisode
import com.opoojkk.podium.data.subscription.SubscriptionExporter
import com.opoojkk.podium.data.subscription.SubscriptionImporter
import com.opoojkk.podium.presentation.HomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PodcastRepository(
    private val dao: PodcastDao,
    private val feedService: PodcastFeedService,
    private val appSettings: com.opoojkk.podium.data.local.AppSettings,
) {
    private val exporter = SubscriptionExporter()
    private val importer = SubscriptionImporter()

    fun observeSubscriptions(): Flow<List<Podcast>> = dao.observePodcasts()

    fun observeRecentUpdates(): Flow<List<EpisodeWithPodcast>> = dao.observeRecentEpisodes(20)

    fun observeRecentListening(): Flow<List<EpisodeWithPodcast>> = dao.observeRecentListening(10)

    // 首页专用：最近收听显示不同播客的最多6集，最近更新按单集发布时间排序显示最多6集
    fun observeHomeState(): Flow<HomeUiState> = combine(
        dao.observeRecentListeningUnique(6),  // 每个播客只显示最近播放的一集
        dao.observeRecentEpisodes(6),         // 按发布时间排序，不同播客的单集可以穿插
    ) { listening, updates ->
        HomeUiState(
            recentPlayed = listening,
            recentUpdates = updates,
            isLoading = false,
        )
    }

    // 查看更多页面使用
    fun observeAllRecentListening(): Flow<List<EpisodeWithPodcast>> = dao.observeAllRecentListening()

    fun observeAllRecentUpdates(): Flow<List<EpisodeWithPodcast>> = dao.observeAllRecentEpisodes()

    // 获取特定播客的所有单集
    fun observePodcastEpisodes(podcastId: String): Flow<List<EpisodeWithPodcast>> = 
        dao.observeEpisodesWithPodcast(podcastId)

    fun observeDownloads(): Flow<Map<String, DownloadStatus>> =
        dao.observeDownloads().map { rows ->
            rows.mapValues { (episodeId, statusTriple) ->
                when (statusTriple.first) {
                    "completed" -> DownloadStatus.Completed(episodeId, statusTriple.third ?: "")
                    "failed" -> DownloadStatus.Failed(episodeId, statusTriple.third ?: "")
                    "in_progress" -> DownloadStatus.InProgress(episodeId, statusTriple.second)
                    else -> DownloadStatus.Idle(episodeId)
                }
            }
        }

    suspend fun getEpisodeIdsForPodcast(podcastId: String): List<String> =
        dao.getEpisodeIdsForPodcast(podcastId)

    suspend fun getEpisodeWithPodcast(episodeId: String): EpisodeWithPodcast? =
        dao.getEpisodeWithPodcast(episodeId)

    data class SubscriptionResult(
        val podcast: Podcast,
        val episodes: List<Episode>,
    )

    data class OpmlImportResult(
        val imported: Int,
        val skipped: Int,
        val failures: List<OpmlImportError>,
    ) {
        val hasErrors: Boolean get() = failures.isNotEmpty()
    }

    data class OpmlImportError(
        val feedUrl: String,
        val reason: String?,
    )

    enum class ExportFormat {
        OPML,
        JSON
    }

    suspend fun subscribe(feedUrl: String, autoDownload: Boolean = false): SubscriptionResult {
        try {
            // Check if a podcast with this feedUrl already exists
            println("🔍 Repository: Checking for existing podcast with feedUrl: $feedUrl")
            val existingPodcast = dao.getPodcastByFeedUrl(feedUrl)
            
            // If already subscribed, throw DuplicateSubscriptionException
            if (existingPodcast != null) {
                println("⚠️ Repository: Found existing podcast: ${existingPodcast.title}")
                throw DuplicateSubscriptionException(
                    podcastTitle = existingPodcast.title,
                    feedUrl = feedUrl
                )
            }
            
            println("✅ Repository: No existing podcast found, proceeding with subscription")
            // Fetch the feed data
            val feed = feedService.fetch(feedUrl)
            
            // Create new podcast with generated ID
            val podcast = feed.toPodcast(autoDownload)
            val episodes = feed.episodes.map { it.toEpisode(podcast) }
            
            dao.upsertPodcast(podcast)
            dao.upsertEpisodes(podcast.id, episodes)
            return SubscriptionResult(podcast, episodes)
        } catch (e: DuplicateSubscriptionException) {
            // Re-throw duplicate subscription exception
            println("⚠️ Repository: Re-throwing DuplicateSubscriptionException")
            throw e
        } catch (e: Exception) {
            println("❌ Repository: Subscription failed with exception: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    suspend fun refreshSubscriptions(): Map<String, List<Episode>> {
        val podcasts = observeSubscriptions().first()
        val newEpisodesByPodcast = mutableMapOf<String, List<Episode>>()

        podcasts.forEach { podcast ->
            runCatching {
                // 获取当前已有的节目ID列表
                val existingEpisodes = observePodcastEpisodes(podcast.id).first()
                val existingEpisodeIds = existingEpisodes.map { it.episode.id }.toSet()

                val feed = feedService.fetch(podcast.feedUrl)
                // Use the existing podcast's ID and autoDownload setting when updating
                val updatedPodcast = feed.toPodcast(podcast.autoDownload).copy(id = podcast.id)
                val allEpisodes = feed.episodes.map { it.toEpisode(updatedPodcast) }

                // 找出新的节目
                val newEpisodes = allEpisodes.filter { it.id !in existingEpisodeIds }
                if (newEpisodes.isNotEmpty()) {
                    newEpisodesByPodcast[podcast.id] = newEpisodes
                }

                dao.upsertPodcast(updatedPodcast)
                dao.upsertEpisodes(updatedPodcast.id, allEpisodes)
            }
        }

        // Update last global update timestamp
        appSettings.updateLastGlobalUpdate(kotlinx.datetime.Clock.System.now())

        return newEpisodesByPodcast
    }

    /**
     * Refresh a single podcast subscription.
     */
    suspend fun refreshPodcast(podcastId: String): List<Episode> {
        val podcasts = observeSubscriptions().first()
        val podcast = podcasts.find { it.id == podcastId } ?: return emptyList()

        return runCatching {
            // 获取当前已有的节目ID列表
            val existingEpisodes = observePodcastEpisodes(podcast.id).first()
            val existingEpisodeIds = existingEpisodes.map { it.episode.id }.toSet()

            val feed = feedService.fetch(podcast.feedUrl)
            // Use the existing podcast's ID and autoDownload setting when updating
            val updatedPodcast = feed.toPodcast(podcast.autoDownload).copy(id = podcast.id)
            val allEpisodes = feed.episodes.map { it.toEpisode(updatedPodcast) }

            // 找出新的节目
            val newEpisodes = allEpisodes.filter { it.id !in existingEpisodeIds }

            dao.upsertPodcast(updatedPodcast)
            dao.upsertEpisodes(updatedPodcast.id, allEpisodes)

            newEpisodes
        }.getOrElse { emptyList() }
    }

    /**
     * Check if an automatic update should be performed based on user settings.
     */
    fun shouldAutoUpdate(): Boolean = appSettings.shouldUpdate()

    /**
     * Get the current update interval setting.
     */
    fun getUpdateInterval(): com.opoojkk.podium.data.model.UpdateInterval = appSettings.getUpdateInterval()

    /**
     * Set the update interval preference.
     */
    suspend fun setUpdateInterval(interval: com.opoojkk.podium.data.model.UpdateInterval) {
        appSettings.setUpdateInterval(interval)
    }

    /**
     * Import subscriptions from OPML or JSON format.
     * The format is automatically detected.
     */
    suspend fun importSubscriptions(content: String): OpmlImportResult {
        val importResult = importer.import(content)
        if (importResult.feedUrls.isEmpty()) {
            return OpmlImportResult(
                imported = 0,
                skipped = 0,
                failures = emptyList(),
            )
        }

        var imported = 0
        var skipped = 0
        val failures = mutableListOf<OpmlImportError>()

        for (feedInfo in importResult.feedUrls) {
            val normalizedUrl = feedInfo.feedUrl.trim()
            if (normalizedUrl.isEmpty()) continue

            val alreadySubscribed = dao.getPodcastByFeedUrl(normalizedUrl) != null
            if (alreadySubscribed) {
                skipped += 1
                continue
            }

            runCatching {
                subscribe(normalizedUrl, feedInfo.autoDownload)
                imported += 1
            }.onFailure { throwable ->
                when (throwable) {
                    is DuplicateSubscriptionException -> {
                        skipped += 1
                    }

                    else -> {
                        failures += OpmlImportError(
                            feedUrl = normalizedUrl,
                            reason = throwable.message,
                        )
                    }
                }
            }
        }

        return OpmlImportResult(
            imported = imported,
            skipped = skipped,
            failures = failures.toList(),
        )
    }

    /**
     * Legacy OPML import method for backwards compatibility.
     */
    suspend fun importOpml(opml: String): OpmlImportResult {
        return importSubscriptions(opml)
    }

    /**
     * Export subscriptions in the specified format.
     */
    suspend fun exportSubscriptions(format: ExportFormat = ExportFormat.OPML): String {
        val podcasts = observeSubscriptions().first()
        return when (format) {
            ExportFormat.OPML -> exporter.exportAsOpml(podcasts)
            ExportFormat.JSON -> exporter.exportAsJson(podcasts)
        }
    }

    /**
     * Legacy OPML export method for backwards compatibility.
     */
    suspend fun exportOpml(): String {
        return exportSubscriptions(ExportFormat.OPML)
    }

    suspend fun savePlayback(progress: PlaybackProgress) {
        dao.updatePlayback(progress)
    }

    suspend fun playbackForEpisode(episodeId: String): PlaybackProgress? = dao.playbackForEpisode(episodeId)

    suspend fun getLastPlayedEpisode(): Pair<Episode, PlaybackProgress>? = dao.getLastPlayedEpisode()

    suspend fun setAutoDownload(podcastId: String, enabled: Boolean) {
        dao.updateAutoDownload(podcastId, enabled)
    }

    suspend fun saveDownloadStatus(status: DownloadStatus) {
        val (dbStatus, progress, path) = when (status) {
            is DownloadStatus.Completed -> Triple("completed", 1f, status.filePath)
            is DownloadStatus.Failed -> Triple("failed", 0f, status.reason)
            is DownloadStatus.InProgress -> Triple("in_progress", status.progress, null)
            is DownloadStatus.Idle -> Triple("idle", 0f, null)
        }
        dao.upsertDownloadStatus(
            episodeId = status.episodeId,
            status = dbStatus,
            progress = progress,
            filePath = path,
        )
    }

    suspend fun deleteSubscription(podcastId: String) {
        dao.deletePodcast(podcastId)
    }

    suspend fun renameSubscription(podcastId: String, newTitle: String) {
        dao.updatePodcastTitle(podcastId, newTitle)
    }

    // Playlist-related methods
    fun observePlaylist(): Flow<List<PlaylistItem>> = dao.observePlaylist()

    suspend fun markEpisodeCompleted(episodeId: String) {
        dao.markEpisodeCompleted(episodeId)
    }

    suspend fun removeFromPlaylist(episodeId: String) {
        dao.removeFromPlaylist(episodeId)
    }

    suspend fun addToPlaylist(episodeId: String) {
        dao.addToPlaylist(episodeId)
    }

    private fun com.opoojkk.podium.data.rss.PodcastFeed.toPodcast(autoDownload: Boolean): Podcast = Podcast(
        id = id,
        title = title,
        description = description,
        artworkUrl = artworkUrl,
        feedUrl = feedUrl,
        lastUpdated = lastUpdated,
        autoDownload = autoDownload,
    )

    private fun RssEpisode.toEpisode(podcast: Podcast): Episode = Episode(
        id = id,
        podcastId = podcast.id,
        podcastTitle = podcast.title,
        title = title,
        description = description,
        audioUrl = audioUrl,
        publishDate = publishDate,
        duration = duration,
        imageUrl = imageUrl ?: podcast.artworkUrl,
        chapters = chapters,
    )

}
