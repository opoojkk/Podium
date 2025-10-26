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

    // 首页专用：最近收听显示不同播客的最多6集，最近更新显示不同播客各最新一集的最多6集
    fun observeHomeState(): Flow<HomeUiState> = combine(
        dao.observeRecentListeningUnique(6),  // 每个播客只显示最近播放的一集
        dao.observeRecentEpisodesUnique(6),   // 每个播客只显示最新的一集
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

    suspend fun subscribe(feedUrl: String, autoDownload: Boolean = true): SubscriptionResult {
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
        
        return newEpisodesByPodcast
    }

    suspend fun importOpml(opml: String): OpmlImportResult {
        val urls = extractOpmlFeedUrls(opml)
        if (urls.isEmpty()) {
            return OpmlImportResult(
                imported = 0,
                skipped = 0,
                failures = emptyList(),
            )
        }

        var imported = 0
        var skipped = 0
        val failures = mutableListOf<OpmlImportError>()

        for (url in urls) {
            val normalizedUrl = url.trim()
            if (normalizedUrl.isEmpty()) continue

            val alreadySubscribed = dao.getPodcastByFeedUrl(normalizedUrl) != null
            if (alreadySubscribed) {
                skipped += 1
                continue
            }

            runCatching {
                subscribe(normalizedUrl)
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

    suspend fun exportOpml(): String {
        val podcasts = observeSubscriptions().first()
        val outlines = podcasts.joinToString(separator = "\n") { podcast ->
            val escapedTitle = escapeXml(podcast.title)
            val escapedUrl = escapeXml(podcast.feedUrl)
            "    <outline type=\"rss\" text=\"$escapedTitle\" title=\"$escapedTitle\" xmlUrl=\"$escapedUrl\" />"
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
        private val outlineTagRegex = Regex("<outline\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        private val outlineAttributeRegex = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(['\"])(.*?)\\2", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val numericEntityRegex = Regex("&#(x?[0-9A-Fa-f]+);")

        private fun extractOpmlFeedUrls(opml: String): List<String> {
            val urls = LinkedHashSet<String>()
            outlineTagRegex.findAll(opml).forEach { match ->
                val attributes = buildMap {
                    outlineAttributeRegex.findAll(match.value).forEach { attr ->
                        val key = attr.groupValues[1].lowercase()
                        val value = decodeXmlEntities(attr.groupValues[3])
                        put(key, value)
                    }
                }
                val xmlUrl = attributes["xmlurl"] ?: attributes["url"] ?: attributes["rssurl"]
                if (!xmlUrl.isNullOrBlank()) {
                    urls += xmlUrl.trim()
                }
            }
            return urls.toList()
        }

        private fun escapeXml(raw: String): String =
            buildString(raw.length) {
                raw.forEach { ch ->
                    when (ch) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&apos;")
                        else -> append(ch)
                    }
                }
            }

        private fun decodeXmlEntities(raw: String): String {
            val namedDecoded = raw
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")

            return numericEntityRegex.replace(namedDecoded) { match ->
                val value = match.groupValues[1]
                val codePoint = if (value.startsWith("x") || value.startsWith("X")) {
                    value.substring(1).toIntOrNull(16)
                } else {
                    value.toIntOrNull()
                }
                codePoint?.let { cp -> codePointToString(cp) } ?: match.value
            }
        }

        private fun codePointToString(codePoint: Int): String? = when {
            codePoint < 0 -> null
            codePoint <= 0xFFFF -> runCatching { codePoint.toChar().toString() }.getOrNull()
            codePoint <= 0x10FFFF -> {
                val high = ((codePoint - 0x10000) shr 10) + 0xD800
                val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
                if (high in 0xD800..0xDBFF && low in 0xDC00..0xDFFF) {
                    buildString(2) {
                        append(high.toChar())
                        append(low.toChar())
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
