package com.opoojkk.podium.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.opoojkk.podium.util.Logger

/**
 * Repository for searching podcasts and episodes using Apple Podcast Search API (iTunes Search API)
 */
class ApplePodcastSearchRepository(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://itunes.apple.com/search"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Search for podcasts by name
     */
    suspend fun searchPodcast(query: String, limit: Int = 5): Result<List<ApplePodcastResult>> = withContext(Dispatchers.Default) {
        try {
            val requestUrl = "$BASE_URL?term=$query&entity=podcast&limit=$limit&country=cn"
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 开始请求播客搜索: query=$query, limit=$limit" }
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 请求URL: $requestUrl" }
            val responseText = httpClient.get(BASE_URL) {
                parameter("term", query)
                parameter("entity", "podcast")
                parameter("limit", limit)
                parameter("country", "cn") // Search in China store for better Chinese podcast results
            }.bodyAsText()

            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 收到播客搜索响应: ${responseText.length} 字符" }
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 响应内容: ${responseText.take(500)}..." }
            val response = json.decodeFromString<ApplePodcastSearchResponse>(responseText)
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 播客搜索结果数量: ${response.results.size}" }
            if (response.results.isNotEmpty()) {
                Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 第一个结果: ${response.results.first().collectionName}" }
            }
            Result.success(response.results)
        } catch (e: Exception) {
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 播客搜索请求失败: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Search for podcast episodes by title (generic search, not limited to specific podcast)
     */
    suspend fun searchEpisodes(query: String, limit: Int = 20): Result<List<ApplePodcastEpisodeResult>> = withContext(Dispatchers.Default) {
        try {
            val requestUrl = "$BASE_URL?term=$query&entity=podcastEpisode&limit=$limit&country=cn"
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 开始请求单集搜索: query=$query, limit=$limit" }
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 请求URL: $requestUrl" }
            val responseText = httpClient.get(BASE_URL) {
                parameter("term", query)
                parameter("entity", "podcastEpisode")
                parameter("limit", limit)
                parameter("country", "cn")
            }.bodyAsText()

            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 收到单集搜索响应: ${responseText.length} 字符" }
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 响应内容: ${responseText.take(500)}..." }
            val response = json.decodeFromString<ApplePodcastEpisodeSearchResponse>(responseText)
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 单集搜索结果数量: ${response.results.size}" }
            if (response.results.isNotEmpty()) {
                Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 第一个结果: ${response.results.first().trackName} - ${response.results.first().collectionName}" }
            }
            Result.success(response.results)
        } catch (e: Exception) {
            Logger.d("ApplePodcastSearchRepository") { "🌐 [iTunes API] 单集搜索请求失败: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Search for podcast episodes by title (within a specific podcast)
     */
    suspend fun searchEpisode(podcastName: String, episodeTitle: String, limit: Int = 5): Result<List<ApplePodcastEpisodeResult>> = withContext(Dispatchers.Default) {
        try {
            // Search for the podcast first to get its collection ID
            val podcastResponseText = httpClient.get(BASE_URL) {
                parameter("term", podcastName)
                parameter("entity", "podcast")
                parameter("limit", 1)
                parameter("country", "cn")
            }.bodyAsText()

            val podcastResponse = json.decodeFromString<ApplePodcastSearchResponse>(podcastResponseText)

            if (podcastResponse.results.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Then search for episodes in that podcast
            val collectionId = podcastResponse.results.first().collectionId
            val episodeResponseText = httpClient.get(BASE_URL) {
                parameter("term", episodeTitle)
                parameter("entity", "podcastEpisode")
                parameter("limit", limit)
                parameter("country", "cn")
            }.bodyAsText()

            val episodeResponse = json.decodeFromString<ApplePodcastEpisodeSearchResponse>(episodeResponseText)

            // Filter episodes that belong to this podcast
            val matchingEpisodes = episodeResponse.results.filter {
                it.collectionId == collectionId
            }

            Result.success(matchingEpisodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class ApplePodcastSearchResponse(
    val resultCount: Int,
    val results: List<ApplePodcastResult>
)

@Serializable
data class ApplePodcastResult(
    val collectionId: Long,
    val collectionName: String,
    val artistName: String,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val feedUrl: String,
    val trackCount: Int? = null,
    val primaryGenreName: String? = null,
    val genres: List<String>? = null
)

@Serializable
data class ApplePodcastEpisodeSearchResponse(
    val resultCount: Int,
    val results: List<ApplePodcastEpisodeResult>
)

@Serializable
data class ApplePodcastEpisodeResult(
    val trackId: Long,
    val trackName: String,
    val collectionId: Long,
    val collectionName: String,
    val artistName: String? = null,
    @SerialName("episodeUrl")
    val audioUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val releaseDate: String,
    val description: String? = null,
    @SerialName("trackTimeMillis")
    val durationMs: Long? = null,
    val feedUrl: String? = null
)
