package com.opoojkk.podium.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.util.ChapterSerializer
import com.opoojkk.podium.db.PodcastDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class PodcastDao(private val database: PodcastDatabase) {

    private val queries = database.podcastQueries

    // Expose queries for AppSettings
    val podcastQueries = queries

    fun observePodcasts(): Flow<List<Podcast>> =
        queries.selectAllPodcasts { id, title, description, artworkUrl, feedUrl, lastUpdated, autoDownload ->
            mapPodcast(id, title, description, artworkUrl, feedUrl, lastUpdated, autoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeEpisodes(podcastId: String): Flow<List<Episode>> =
        queries.selectEpisodesByPodcast(podcastId) { id, podcastId, podcastTitle, title, description, audioUrl, publishDate, duration, imageUrl, chapters ->
            mapEpisode(id, podcastId, podcastTitle, title, description, audioUrl, publishDate, duration, imageUrl, chapters)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeEpisodesWithPodcast(podcastId: String): Flow<List<EpisodeWithPodcast>> =
        queries.selectEpisodesWithPodcastByPodcastId(podcastId) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentEpisodes(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentEpisodes(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeRecentEpisodesUnique(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentEpisodesUnique(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    fun observeAllRecentEpisodes(): Flow<List<EpisodeWithPodcast>> =
        queries.selectAllRecentEpisodes { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun searchEpisodes(query: String, limit: Int = 30, offset: Int = 0): List<EpisodeWithPodcast> =
        withContext(Dispatchers.Default) {
            val sanitized = query.trim()
            if (sanitized.isEmpty()) {
                return@withContext emptyList()
            }
            val pattern = "%$sanitized%"
            val cappedLimit = limit.coerceAtMost(50).coerceAtLeast(1)
            val cappedOffset = offset.coerceAtLeast(0)
            queries.searchEpisodes(pattern, pattern, cappedLimit.toLong(), cappedOffset.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
                mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
            }
                .executeAsList()
        }

    fun observeRecentListening(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentPlayback(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.first } }

    fun observeRecentListeningUnique(limit: Int): Flow<List<EpisodeWithPodcast>> =
        queries.selectRecentPlaybackUnique(limit.toLong()) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist)
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.first } }

    fun observeAllRecentListening(): Flow<List<EpisodeWithPodcast>> =
        queries.selectAllRecentPlayback { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist)
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
                    chapters = if (episode.chapters.isNotEmpty()) ChapterSerializer.serialize(episode.chapters) else null,
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
            durationMs = progress.durationMs,
            updatedAt = progress.updatedAt.toEpochMilliseconds(),
            isCompleted = if (progress.isCompleted) 1 else 0,
            addedToPlaylist = if (progress.addedToPlaylist) 1 else 0,
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

    suspend fun getEpisodeIdsForPodcast(podcastId: String): List<String> =
        queries.selectExistingEpisodeIds(podcastId)
            .executeAsList()

    suspend fun getEpisodeWithPodcast(episodeId: String): EpisodeWithPodcast? =
        queries.selectEpisodeWithPodcastByEpisodeId(episodeId) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload ->
            mapEpisodeWithPodcast(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload)
        }
            .executeAsOneOrNull()

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
        return queries.selectPlaybackForEpisode(episodeId) { episodeId_, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            PlaybackProgress(
                episodeId = episodeId_,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = Instant.fromEpochMilliseconds(updatedAt),
                isCompleted = isCompleted != 0L,
                addedToPlaylist = addedToPlaylist != 0L,
            )
        }
            .executeAsOneOrNull()
    }

    suspend fun getLastPlayedEpisode(): Pair<Episode, PlaybackProgress>? {
        return queries.selectRecentPlayback(1) { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            mapPlaybackWithEpisode(id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist)
        }
            .executeAsOneOrNull()
            ?.let { (episodeWithPodcast, progress) ->
                episodeWithPodcast.episode to progress
            }
    }

    suspend fun deletePodcast(podcastId: String) {
        queries.transaction {
            queries.deleteDownloadsByPodcastId(podcastId)
            queries.deletePlaybackByPodcastId(podcastId)
            queries.removeEpisodesForPodcast(podcastId)
            queries.deletePodcastById(podcastId)
        }
    }

    suspend fun updatePodcastTitle(podcastId: String, newTitle: String) {
        queries.updatePodcastTitle(
            title = newTitle,
            id = podcastId,
        )
    }

    suspend fun getPodcastByFeedUrl(feedUrl: String): Podcast? {
        println("🔍 DAO: Looking up podcast by feedUrl: $feedUrl")
        val result = queries.selectPodcastByFeedUrl(feedUrl) { id, title, description, artworkUrl, feedUrl_, lastUpdated, autoDownload ->
            mapPodcast(id, title, description, artworkUrl, feedUrl_, lastUpdated, autoDownload)
        }
            .executeAsOneOrNull()
        println("🔍 DAO: Query result: ${if (result != null) "Found: ${result.title}" else "Not found"}")
        return result
    }

    // Playlist-related methods
    fun observePlaylist(): Flow<List<com.opoojkk.podium.data.model.PlaylistItem>> =
        queries.selectPlaylistEpisodes { id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters, podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed, podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt, isCompleted, addedToPlaylist ->
            val (episodeWithPodcast, progress) = mapPlaybackWithEpisode(
                id, podcastId, title, description, audioUrl, publishDate, duration, imageUrl, chapters,
                podcastId_, podcastTitle, podcastDescription, podcastArtwork, podcastFeed,
                podcastLastUpdated, podcastAutoDownload, positionMs, durationMs, updatedAt,
                isCompleted, addedToPlaylist
            )
            com.opoojkk.podium.data.model.PlaylistItem(
                episode = episodeWithPodcast.episode,
                podcast = episodeWithPodcast.podcast,
                progress = progress
            )
        }
            .asFlow()
            .mapToList(Dispatchers.Default)

    suspend fun markEpisodeCompleted(episodeId: String) {
        queries.markEpisodeCompleted(episodeId)
    }

    suspend fun removeFromPlaylist(episodeId: String) {
        queries.removeFromPlaylist(episodeId)
    }

    suspend fun addToPlaylist(episodeId: String) {
        queries.addToPlaylist(episodeId)
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
        chapters: String?,
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
        chapters = ChapterSerializer.deserialize(chapters),
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
        chapters: String?,
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
            chapters = ChapterSerializer.deserialize(chapters),
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
        chapters: String?,
        podcastId_: String,
        podcastTitle: String,
        podcastDescription: String,
        podcastArtwork: String?,
        podcastFeed: String,
        podcastLastUpdated: Long,
        podcastAutoDownload: Long,
        positionMs: Long,
        durationMs: Long?,
        updatedAt: Long,
        isCompleted: Long,
        addedToPlaylist: Long,
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
            chapters = ChapterSerializer.deserialize(chapters),
        )
        val progress = PlaybackProgress(
            episodeId = id,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            isCompleted = isCompleted != 0L,
            addedToPlaylist = addedToPlaylist != 0L,
        )
        return EpisodeWithPodcast(episode, podcast) to progress
    }
}
