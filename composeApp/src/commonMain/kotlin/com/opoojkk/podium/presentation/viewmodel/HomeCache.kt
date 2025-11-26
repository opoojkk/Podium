package com.opoojkk.podium.presentation.viewmodel

import com.opoojkk.podium.data.local.AppSettings
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.data.model.recommended.PodcastCategory
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedEpisode(
    val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishDateEpochMs: Long,
    val duration: Long?,
    val imageUrl: String?,
)

@Serializable
private data class CachedPodcast(
    val id: String,
    val title: String,
    val description: String,
    val artworkUrl: String?,
    val feedUrl: String,
    val lastUpdatedEpochMs: Long,
    val autoDownload: Boolean,
)

@Serializable
private data class CachedEpisodeWithPodcast(
    val episode: CachedEpisode,
    val podcast: CachedPodcast,
)

@Serializable
data class CachedHomePayload(
    val categories: List<PodcastCategory> = emptyList(),
    val hotEpisodes: List<CachedEpisodeWithPodcast> = emptyList(),
    val hotPodcasts: List<CachedPodcast> = emptyList(),
    val newEpisodes: List<CachedEpisodeWithPodcast> = emptyList(),
    val newPodcasts: List<CachedPodcast> = emptyList(),
    val cachedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
) {
    fun isFresh(ttlMillis: Long): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return now - cachedAtEpochMs < ttlMillis
    }

    fun toViewState(isLoading: Boolean, fromCache: Boolean): HomeViewState {
        return HomeViewState(
            categories = categories,
            hotEpisodes = hotEpisodes.map { it.toDomain() },
            hotPodcasts = hotPodcasts.map { it.toDomain() },
            newEpisodes = newEpisodes.map { it.toDomain() },
            newPodcasts = newPodcasts.map { it.toDomain() },
            isLoading = isLoading,
            errorMessage = null,
            sectionErrors = emptyMap(),
            lastUpdated = Instant.fromEpochMilliseconds(cachedAtEpochMs),
            isFromCache = fromCache,
        )
    }
}

private fun Episode.toCached(): CachedEpisode = CachedEpisode(
    id = id,
    podcastId = podcastId,
    podcastTitle = podcastTitle,
    title = title,
    description = description,
    audioUrl = audioUrl,
    publishDateEpochMs = publishDate.toEpochMilliseconds(),
    duration = duration,
    imageUrl = imageUrl,
)

private fun Podcast.toCached(): CachedPodcast = CachedPodcast(
    id = id,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    lastUpdatedEpochMs = lastUpdated.toEpochMilliseconds(),
    autoDownload = autoDownload,
)

private fun CachedEpisode.toDomain(): Episode = Episode(
    id = id,
    podcastId = podcastId,
    podcastTitle = podcastTitle,
    title = title,
    description = description,
    audioUrl = audioUrl,
    publishDate = Instant.fromEpochMilliseconds(publishDateEpochMs),
    duration = duration,
    imageUrl = imageUrl,
)

private fun CachedPodcast.toDomain(): Podcast = Podcast(
    id = id,
    title = title,
    description = description,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    lastUpdated = Instant.fromEpochMilliseconds(lastUpdatedEpochMs),
    autoDownload = autoDownload,
)

private fun CachedEpisodeWithPodcast.toDomain(): EpisodeWithPodcast = EpisodeWithPodcast(
    episode = episode.toDomain(),
    podcast = podcast.toDomain(),
)

private fun EpisodeWithPodcast.toCached(): CachedEpisodeWithPodcast = CachedEpisodeWithPodcast(
    episode = episode.toCached(),
    podcast = podcast.toCached(),
)

fun HomeViewState.toCachedPayload(now: Instant = Clock.System.now()): CachedHomePayload = CachedHomePayload(
    categories = categories,
    hotEpisodes = hotEpisodes.map { it.toCached() },
    hotPodcasts = hotPodcasts.map { it.toCached() },
    newEpisodes = newEpisodes.map { it.toCached() },
    newPodcasts = newPodcasts.map { it.toCached() },
    cachedAtEpochMs = now.toEpochMilliseconds(),
)

class HomeCache(
    private val appSettings: AppSettings,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): CachedHomePayload? {
        val serialized = appSettings.getHomeCache() ?: return null
        return runCatching { json.decodeFromString(CachedHomePayload.serializer(), serialized) }.getOrNull()
    }

    suspend fun save(state: HomeViewState) {
        runCatching {
            val payload = state.toCachedPayload()
            appSettings.saveHomeCache(json.encodeToString(payload))
        }
    }
}
