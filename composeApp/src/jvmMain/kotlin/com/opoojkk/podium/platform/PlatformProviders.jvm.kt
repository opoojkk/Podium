package com.opoojkk.podium.platform

import com.opoojkk.podium.data.local.DatabaseDriverFactory
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.download.DefaultPodcastDownloadManager
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.player.JvmRustPodcastPlayer
import com.opoojkk.podium.player.PodcastPlayer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

actual fun createPlatformHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(Logging)
}

actual fun providePodcastPlayer(context: PlatformContext): PodcastPlayer = JvmRustPodcastPlayer()

actual fun provideDownloadManager(
    context: PlatformContext,
    scope: CoroutineScope,
    onStatusChanged: (DownloadStatus) -> Unit,
): PodcastDownloadManager = DefaultPodcastDownloadManager(scope, onStatusChanged)

actual fun provideDatabaseDriverFactory(context: PlatformContext): DatabaseDriverFactory = DatabaseDriverFactory()
