package com.opoojkk.podium.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.db.PodcastDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class PodcastDao(private val database: PodcastDatabase) {

    private val queries = database.podcastQueries

    fun observePodcasts(): Flow<List<Podcast>> =
        queries.selectAllPodcasts { id, title, description, artworkUrl, feedUrl, lastUpdated, autoDownload ->
            mapPodcast(id, title, description, artworkUrl, feedUrl, lastUpdated, autoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeEpisodes(podcastId: String): Flow<List<Episode>> =
        queries.selectEpisodesByPodcast(podcastId) { id, podcastId, podcastTitle, title, description, audioUrl, publishDate, duration, imageUrl ->
            mapEpisode(id, podcastId, podcastTitle, title, description, audioUrl, publishDate, duration, imageUrl)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentEpisodes(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentEpisodesUnique(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentEpisodesUnique(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeAllRecentEpisodes(): Flow<List<EpisodeWithPodcast>> =
        queries.selectAllRecentEpisodes { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentListening(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentPlayback(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.first } }

    fun observeRecentListeningUnique(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentPlaybackUnique(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.first } }

    fun observeAllRecentListening(): Flow<List<EpisodeWithPodcast>> =
        queries.selectAllRecentPlayback { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, updatedAt)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.first } }

    suspend fun upsertPodcast(podcast: Podcast) {
        queries.upsertPodcast(
            id = podcast.id,
            title = podcast.title,
            description = podcast.description,
            artworkUrl = podcast.artworkUrl,
            feedUrl = podcast.feedUrl,
            lastUpdated = podcast.lastUpdated.toEpochMilliseconds(),
            autoDownload = if (podcast.autoDownload) 1 else 0,
        )
    }

    suspend fun upsertEpisodes(podcastId: String, episodes: List<Episode>) {
        queries.transaction {
            // Get existing episode IDs to preserve them
            val existingEpisodeIds = queries.selectExistingEpisodeIds(podcastId)
                .executeAsList()
                .toSet()
            
            // Upsert all episodes from the feed (this will update existing ones and add new ones)
            episodes.forEach { episode ->
                queries.upsertEpisode(
                    id = episode.id,
                    podcastId = episode.podcastId,
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    publishDate = episode.publishDate.toEpochMilliseconds(),
                    duration = episode.duration,
                    imageUrl = episode.imageUrl,
                )
            }
            
            // Only remove episodes that are no longer in the feed
            // This preserves episodes that might be temporarily missing from the feed
            val newEpisodeIds = episodes.map { it.id }.toSet()
            val episodesToRemove = existingEpisodeIds - newEpisodeIds
            
            if (episodesToRemove.isNotEmpty()) {
                episodesToRemove.forEach { episodeId ->
                    queries.removeEpisodeById(episodeId)
                }
            }
        }
    }

    suspend fun updatePlayback(progress: PlaybackProgress) {
        queries.upsertPlayback(
            episodeId = progress.episodeId,
            positionMs = progress.positionMs,
            updatedAt = progress.updatedAt.toEpochMilliseconds(),
        )
    }

    suspend fun updateAutoDownload(podcastId: String, enabled: Boolean) {
        queries.updateAutoDownload(
            autoDownload = if (enabled) 1 else 0,
            id = podcastId,
        )
    }

    fun observeDownloads(): Flow<Map<String, Triple<String, Float, String?>>> =
        queries.selectDownloadStatuses { episodeId, status, progress, filePath ->
            episodeId to Triple(status, progress.toFloat(), filePath)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.associate { it }
            }

    suspend fun upsertDownloadStatus(
        episodeId: String,
        status: String,
        progress: Float,
        filePath: String?,
    ) {
        queries.upsertDownload(
            episodeId = episodeId,
            status = status,
            progress = progress.toDouble(),
            filePath = filePath,
        )
    }

    suspend fun playbackForEpisode(episodeId: String): PlaybackProgress? {
        return queries.selectPlaybackForEpisode(episodeId) { episodeId_, positionMs, updatedAt ->
            PlaybackProgress(
                episodeId = episodeId_,
                positionMs = positionMs,
                updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            )
        }
            .executeAsOneOrNull()
    }

    suspend fun deletePodcast(podcastId: String) {
        queries.deletePodcastById(podcastId)
    }

    suspend fun updatePodcastTitle(podcastId: String, newTitle: String) {
        queries.updatePodcastTitle(
            title = newTitle,
            id = podcastId,
        )
    }

    private fun mapPodcast(
        id: String,
        title: String,
        description: String,
        artworkUrl: String?,
        feedUrl: String,
        lastUpdated: Long,
        autoDownload: Long,
    ): Podcast = Podcast(
        id = id,
        title = title,
        description = description,
        artworkUrl = artworkUrl,
        feedUrl = feedUrl,
        lastUpdated = Instant.fromEpochMilliseconds(lastUpdated),
        autoDownload = autoDownload != 0L,
    )

    private fun mapEpisode(
        id: String,
        podcastId: String,
        podcastTitle: String,
        title: String,
        description: String,
        audioUrl: String,
        publishDate: Long,
        duration: Long?,
        imageUrl: String?,
    ): Episode = Episode(
        id = id,
        podcastId = podcastId,
        podcastTitle = podcastTitle,
        title = title,
        description = description,
        audioUrl = audioUrl,
        publishDate = Instant.fromEpochMilliseconds(publishDate),
        duration = duration,
        imageUrl = imageUrl,
    )

    private fun mapEpisodeWithPodcast(
        id: String,
        podcastId: String,
        title: String,
        description: String,
        audioUrl: String,
        publishDate: Long,
        duration: Long?,
        imageUrl: String?,
        podcastId_: String,
        podcastTitle: String,
        podcastDescription: String,
        podcastArtwork: String?,
        podcastFeed: String,
        podcastLastUpdated: Long,
        podcastAutoDownload: Long,
    ): EpisodeWithPodcast {
        val podcast = Podcast(
            id = podcastId_,
            title = podcastTitle,
            description = podcastDescription,
            artworkUrl = podcastArtwork,
            feedUrl = podcastFeed,
            lastUpdated = Instant.fromEpochMilliseconds(podcastLastUpdated),
            autoDownload = podcastAutoDownload != 0L,
        )
        val episode = Episode(
            id = id,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            title = title,
            description = description,
            audioUrl = audioUrl,
            publishDate = Instant.fromEpochMilliseconds(publishDate),
            duration = duration,
            imageUrl = imageUrl,
        )
        return EpisodeWithPodcast(episode, podcast)
    }

    private fun mapPlaybackWithEpisode(
        id: String,
        podcastId: String,
        title: String,
        description: String,
        audioUrl: String,
        publishDate: Long,
        duration: Long?,
        imageUrl: String?,
        podcastId_: String,
        podcastTitle: String,
        podcastDescription: String,
        podcastArtwork: String?,
        podcastFeed: String,
        podcastLastUpdated: Long,
        podcastAutoDownload: Long,
        positionMs: Long,
        updatedAt: Long,
    ): Pair<EpisodeWithPodcast, PlaybackProgress> {
        val podcast = Podcast(
            id = podcastId_,
            title = podcastTitle,
            description = podcastDescription,
            artworkUrl = podcastArtwork,
            feedUrl = podcastFeed,
            lastUpdated = Instant.fromEpochMilliseconds(podcastLastUpdated),
            autoDownload = podcastAutoDownload != 0L,
        )
        val episode = Episode(
            id = id,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            title = title,
            description = description,
            audioUrl = audioUrl,
            publishDate = Instant.fromEpochMilliseconds(publishDate),
            duration = duration,
            imageUrl = imageUrl,
        )
        val progress = PlaybackProgress(
            episodeId = id,
            positionMs = positionMs,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        )
        return EpisodeWithPodcast(episode, podcast) to progress
    }
}
