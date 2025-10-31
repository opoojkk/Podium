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
    val platformContext: PlatformContext,
    val fileOperations: FileOperations,
) {
    fun dispose() {
        scope.cancel()
        httpClient.close()
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
    val fileOperations = createFileOperations(context)
    return PodiumEnvironment(repository, player, downloadManager, httpClient, scope, context, fileOperations)
}

fun PodiumEnvironment.createController(): PodiumController =
    PodiumController(
        repository = repository,
        player = player,
        downloadManager = downloadManager,
        scope = scope,
    )
