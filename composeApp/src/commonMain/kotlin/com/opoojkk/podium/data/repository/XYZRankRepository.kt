package com.opoojkk.podium.data.repository

import com.opoojkk.podium.data.model.xyzrank.XYZRankEpisode
import com.opoojkk.podium.data.model.xyzrank.XYZRankEpisodeResponse
import com.opoojkk.podium.data.model.xyzrank.XYZRankPodcast
import com.opoojkk.podium.data.model.xyzrank.XYZRankPodcastResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Repository for fetching data from XYZRank API
 * Source: https://github.com/eddiehe99/xyzrank
 */
class XYZRankRepository(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL = "https://xyzrank.eddiehe.top"
        private const val HOT_EPISODES_URL = "$BASE_URL/hot_episodes.json"
        private const val HOT_PODCASTS_URL = "$BASE_URL/full.json"
        private const val NEW_EPISODES_URL = "$BASE_URL/hot_episodes_new.json"
        private const val NEW_PODCASTS_URL = "$BASE_URL/new_podcasts.json"
    }

    /**
     * Fetch hot episodes
     */
    suspend fun getHotEpisodes(): Result<List<XYZRankEpisode>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(HOT_EPISODES_URL).body<XYZRankEpisodeResponse>()
            Result.success(response.data.episodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch hot podcasts
     */
    suspend fun getHotPodcasts(): Result<List<XYZRankPodcast>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(HOT_PODCASTS_URL).body<XYZRankPodcastResponse>()
            Result.success(response.data.podcasts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch new episodes
     */
    suspend fun getNewEpisodes(): Result<List<XYZRankEpisode>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(NEW_EPISODES_URL).body<XYZRankEpisodeResponse>()
            Result.success(response.data.episodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch new podcasts
     */
    suspend fun getNewPodcasts(): Result<List<XYZRankPodcast>> = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(NEW_PODCASTS_URL).body<XYZRankPodcastResponse>()
            Result.success(response.data.podcasts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
