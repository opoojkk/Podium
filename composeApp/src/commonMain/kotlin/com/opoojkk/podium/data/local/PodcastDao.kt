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

    private val queries = database.podcastDatabaseQueries

    fun observePodcasts(): Flow<List<Podcast>> =
        queries.selectAllPodcasts(::mapPodcast)
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeEpisodes(podcastId: String): Flow<List<Episode>> =
        queries.selectEpisodesByPodcast(podcastId, ::mapEpisode)
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentEpisodes(limit.toLong(), ::mapEpisodeWithPodcast)
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentListening(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentPlayback(limit.toLong(), ::mapPlaybackWithEpisode)
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
            queries.removeEpisodesForPodcast(podcastId)
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
        queries.selectDownloadStatuses()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.associate { row ->
                    row.episodeId to Triple(row.status, row.progress, row.filePath)
                }
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
            progress = progress,
            filePath = filePath,
        )
    }

    suspend fun playbackForEpisode(episodeId: String): PlaybackProgress? {
        return queries.selectPlaybackForEpisode(episodeId)
            .executeAsOneOrNull()
            ?.let { row ->
                PlaybackProgress(
                    episodeId = row.episodeId,
                    positionMs = row.positionMs,
                    updatedAt = Instant.fromEpochMilliseconds(row.updatedAt),
                )
            }
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
        title: String,
        description: String,
        audioUrl: String,
        publishDate: Long,
        duration: Long?,
        imageUrl: String?,
    ): Episode = Episode(
        id = id,
        podcastId = podcastId,
        podcastTitle = "",
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
