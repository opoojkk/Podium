package com.opoojkk.podium.data.repository

import com.opoojkk.podium.data.model.recommended.PodcastCategory
import com.opoojkk.podium.data.model.recommended.PodcastCollection
import com.opoojkk.podium.data.model.recommended.RecommendedPodcast
import com.opoojkk.podium.data.rss.PodcastFeedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import podium.composeapp.generated.resources.Res

class RecommendedPodcastRepository(
    private val feedService: PodcastFeedService,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedCollection: PodcastCollection? = null
    private val artworkCache = mutableMapOf<String, String?>()

    suspend fun loadPodcastCollection(): Result<PodcastCollection> = withContext(Dispatchers.IO) {
        try {
            if (cachedCollection != null) {
                return@withContext Result.success(cachedCollection!!)
            }

            val jsonText = Res.readBytes("files/chinese_podcasts.json")
                .decodeToString()

            val collection = json.decodeFromString<PodcastCollection>(jsonText)
            cachedCollection = collection

            Result.success(collection)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRandomRecommendedPodcasts(count: Int = 10): Result<List<Pair<RecommendedPodcast, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val collection = loadPodcastCollection().getOrThrow()

                // 只选择有RSS URL的播客
                val allPodcastsWithCategory = collection.categories.flatMap { category ->
                    category.podcasts
                        .filter { !it.rssUrl.isNullOrBlank() }
                        .map { podcast -> podcast to category.name }
                }

                // 随机打乱并取指定数量
                val randomPodcasts = allPodcastsWithCategory
                    .shuffled()
                    .take(count)

                // 从RSS订阅链接中并行加载封面图片
                val podcastsWithArtwork = randomPodcasts.map { (podcast, categoryName) ->
                    async {
                        val artworkUrl = artworkCache.getOrPut(podcast.id) {
                            podcast.rssUrl?.let { rssUrl ->
                                runCatching {
                                    feedService.fetch(rssUrl).artworkUrl
                                }.getOrNull()
                            }
                        }
                        podcast.copy(artworkUrl = artworkUrl) to categoryName
                    }
                }.awaitAll()

                Result.success(podcastsWithArtwork)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getAllCategories(): Result<List<PodcastCategory>> = withContext(Dispatchers.IO) {
        try {
            val collection = loadPodcastCollection().getOrThrow()
            Result.success(collection.categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPodcastsByCategory(categoryId: String): Result<List<RecommendedPodcast>> =
        withContext(Dispatchers.IO) {
            try {
                val collection = loadPodcastCollection().getOrThrow()
                val category = collection.categories.find { it.id == categoryId }
                    ?: return@withContext Result.failure(Exception("Category not found"))

                Result.success(category.podcasts)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Load artwork from RSS feeds for a list of podcasts
     */
    suspend fun loadPodcastsWithArtwork(podcasts: List<RecommendedPodcast>): List<RecommendedPodcast> =
        withContext(Dispatchers.IO) {
            podcasts.map { podcast ->
                async {
                    if (!podcast.rssUrl.isNullOrBlank()) {
                        val artworkUrl = artworkCache.getOrPut(podcast.id) {
                            podcast.rssUrl?.let { rssUrl ->
                                runCatching {
                                    println("Repository: Fetching RSS for ${podcast.name} from $rssUrl")
                                    feedService.fetch(rssUrl).artworkUrl
                                }.onSuccess { url ->
                                    println("Repository: Got artwork for ${podcast.name}: $url")
                                }.onFailure { e ->
                                    println("Repository: Failed to fetch RSS for ${podcast.name}: ${e.message}")
                                }.getOrNull()
                            }
                        }
                        podcast.copy(artworkUrl = artworkUrl)
                    } else {
                        podcast
                    }
                }
            }.awaitAll()
        }
}
