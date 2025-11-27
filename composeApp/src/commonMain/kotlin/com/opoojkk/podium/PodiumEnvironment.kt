package com.opoojkk.podium

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.setSingletonImageLoaderFactory
import com.opoojkk.podium.data.local.PodcastDao
import com.opoojkk.podium.data.repository.ApplePodcastSearchRepository
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.data.rss.PodcastFeedService
import com.opoojkk.podium.data.rss.createDefaultRssParser
import com.opoojkk.podium.db.PodcastDatabase
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.platform.*
import com.opoojkk.podium.player.PodcastPlayer
import com.opoojkk.podium.presentation.PodiumController
import com.opoojkk.podium.ui.components.ImageLoaderConfig
import io.ktor.client.*
import kotlinx.coroutines.*

class PodiumEnvironment internal constructor(
    val repository: PodcastRepository,
    val applePodcastSearchRepository: ApplePodcastSearchRepository,
    val player: PodcastPlayer,
    val downloadManager: PodcastDownloadManager,
    val httpClient: HttpClient,
    internal val scope: CoroutineScope,
    val platformContext: PlatformContext,
    val fileOperations: FileOperations,
    val appSettings: com.opoojkk.podium.data.local.AppSettings,
) {
    fun dispose() {
        scope.cancel()
        httpClient.close()
    }
}

@OptIn(ExperimentalCoilApi::class)
fun createPodiumEnvironment(context: PlatformContext): PodiumEnvironment {
    // 初始化全局ImageLoader - 配置三级缓存策略
    setSingletonImageLoaderFactory { ctx ->
        ImageLoaderConfig.createImageLoader(ctx, debug = false)
    }

    val httpClient = createPlatformHttpClient()
    val driverFactory = provideDatabaseDriverFactory(context)
    val database = PodcastDatabase(driverFactory.createDriver())
    val dao = PodcastDao(database)
    val appSettings = com.opoojkk.podium.data.local.AppSettings(dao.podcastQueries)
    val repository = PodcastRepository(
        dao = dao,
        feedService = PodcastFeedService(httpClient, createDefaultRssParser()),
        appSettings = appSettings,
    )
    val applePodcastSearchRepository = ApplePodcastSearchRepository(httpClient)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val player = providePodcastPlayer(context)
    val downloadManager = provideDownloadManager(context, scope) { status ->
        scope.launch { repository.saveDownloadStatus(status) }
    }
    val fileOperations = createFileOperations(context)
    return PodiumEnvironment(repository, applePodcastSearchRepository, player, downloadManager, httpClient, scope, context, fileOperations, appSettings)
}

fun PodiumEnvironment.createController(): PodiumController =
    PodiumController(
        repository = repository,
        applePodcastSearchRepository = applePodcastSearchRepository,
        player = player,
        downloadManager = downloadManager,
        scope = scope,
    )
