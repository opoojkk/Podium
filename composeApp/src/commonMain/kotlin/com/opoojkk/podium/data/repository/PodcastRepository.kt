package com.opoojkk.podium.data.repository

import com.opoojkk.podium.data.local.PodcastDao
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.rss.PodcastFeedService
import com.opoojkk.podium.data.rss.RssEpisode
import com.opoojkk.podium.presentation.HomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PodcastRepository(
    private val dao: PodcastDao,
    private val feedService: PodcastFeedService,
) {

    fun observeSubscriptions(): Flow<List<Podcast>> = dao.observePodcasts()

    fun observeRecentUpdates(): Flow<List<EpisodeWithPodcast>> = dao.observeRecentEpisodes(20)

    fun observeRecentListening(): Flow<List<EpisodeWithPodcast>> = dao.observeRecentListening(10)

    fun observeHomeState(): Flow<HomeUiState> = combine(
        observeRecentListening(),
        observeRecentUpdates(),
    ) { listening, updates ->
        HomeUiState(
            recentPlayed = listening,
            recentUpdates = updates,
            isLoading = false,
        )
    }

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

    data class SubscriptionResult(
        val podcast: Podcast,
        val episodes: List<Episode>,
    )

    suspend fun subscribe(feedUrl: String, autoDownload: Boolean = true): SubscriptionResult {
        try {
            val feed = feedService.fetch(feedUrl)
            val podcast = feed.toPodcast(autoDownload)
            val episodes = feed.episodes.map { it.toEpisode(podcast) }
            dao.upsertPodcast(podcast)
            dao.upsertEpisodes(podcast.id, episodes)
            return SubscriptionResult(podcast, episodes)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun refreshSubscriptions() {
        val podcasts = observeSubscriptions().first()
        podcasts.forEach { podcast ->
            runCatching {
                val feed = feedService.fetch(podcast.feedUrl)
                val updatedPodcast = feed.toPodcast(podcast.autoDownload)
                dao.upsertPodcast(updatedPodcast)
                dao.upsertEpisodes(updatedPodcast.id, feed.episodes.map { it.toEpisode(updatedPodcast) })
            }
        }
    }

    suspend fun importOpml(opml: String) {
        val urls = opmlOutlineRegex.findAll(opml)
            .mapNotNull { it.groups[1]?.value }
            .toSet()
        urls.forEach { url -> subscribe(url) }
    }

    suspend fun exportOpml(): String {
        val podcasts = observeSubscriptions().first()
        val outlines = podcasts.joinToString(separator = "\n") { podcast ->
            "    <outline type=\"rss\" text=\"${podcast.title}\" xmlUrl=\"${podcast.feedUrl}\"/>"
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<opml version="2.0">
            |  <head>
            |    <title>Podium Subscriptions</title>
            |  </head>
            |  <body>
            |$outlines
            |  </body>
            |</opml>
        """.trimMargin()
    }

    suspend fun savePlayback(progress: PlaybackProgress) {
        dao.updatePlayback(progress)
    }

    suspend fun playbackForEpisode(episodeId: String): PlaybackProgress? = dao.playbackForEpisode(episodeId)

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
    )

    companion object {
        private val opmlOutlineRegex = Regex("xmlUrl=\"([^\"]+)\"")
    }
}
