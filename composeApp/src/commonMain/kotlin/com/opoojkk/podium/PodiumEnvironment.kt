package com.opoojkk.podium

import com.opoojkk.podium.data.local.PodcastDao
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.data.rss.PodcastFeedService
import com.opoojkk.podium.data.rss.SimpleRssParser
import com.opoojkk.podium.db.PodcastDatabase
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.platform.*
import com.opoojkk.podium.player.PodcastPlayer
import com.opoojkk.podium.presentation.PodiumController
import io.ktor.client.*
import kotlinx.coroutines.*

class PodiumEnvironment internal constructor(
    val repository: PodcastRepository,
    val player: PodcastPlayer,
    val downloadManager: PodcastDownloadManager,
    val httpClient: HttpClient,
    internal val scope: CoroutineScope,
) {
    fun dispose() {
        scope.cancel()
        httpClient.close()
        // Release VLCJ resources if it's a DesktopPodcastPlayer
        try {
            if (player::class.simpleName == "DesktopPodcastPlayer") {
                val releaseMethod = player::class.java.getMethod("release")
                releaseMethod.invoke(player)
            }
        } catch (e: Exception) {
            // Ignore if release method doesn't exist
        }
    }
}

fun createPodiumEnvironment(context: PlatformContext): PodiumEnvironment {
    val httpClient = createPlatformHttpClient()
    val driverFactory = provideDatabaseDriverFactory(context)
    val database = PodcastDatabase(driverFactory.createDriver())
    val dao = PodcastDao(database)
    val repository = PodcastRepository(
        dao = dao,
        feedService = PodcastFeedService(httpClient, SimpleRssParser()),
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val player = providePodcastPlayer(context)
    val downloadManager = provideDownloadManager(context, scope) { status ->
        scope.launch { repository.saveDownloadStatus(status) }
    }
    return PodiumEnvironment(repository, player, downloadManager, httpClient, scope)
}

fun PodiumEnvironment.createController(): PodiumController =
    PodiumController(
        repository = repository,
        player = player,
        downloadManager = downloadManager,
        scope = scope,
    )
