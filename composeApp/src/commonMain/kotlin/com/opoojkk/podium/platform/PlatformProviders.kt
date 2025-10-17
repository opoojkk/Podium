package com.opoojkk.podium.platform

import com.opoojkk.podium.data.local.DatabaseDriverFactory
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.download.PodcastDownloadManager
import com.opoojkk.podium.player.PodcastPlayer
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

expect fun createPlatformHttpClient(): HttpClient

expect fun providePodcastPlayer(context: PlatformContext): PodcastPlayer

expect fun provideDownloadManager(
    context: PlatformContext,
    scope: CoroutineScope,
    onStatusChanged: (DownloadStatus) -> Unit,
): PodcastDownloadManager

expect fun provideDatabaseDriverFactory(context: PlatformContext): DatabaseDriverFactory
