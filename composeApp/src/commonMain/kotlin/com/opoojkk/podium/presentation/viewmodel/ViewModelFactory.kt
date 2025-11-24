package com.opoojkk.podium.presentation.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.opoojkk.podium.PodiumEnvironment

/**
 * Remember HomeViewModel with proper dependencies.
 */
@Composable
fun rememberHomeViewModel(environment: PodiumEnvironment): HomeViewModel {
    val scope = rememberCoroutineScope()
    return remember(environment) {
        HomeViewModel(
            recommendedPodcastRepository = com.opoojkk.podium.data.repository.RecommendedPodcastRepository(
                feedService = com.opoojkk.podium.data.rss.PodcastFeedService(
                    httpClient = environment.httpClient,
                    parser = com.opoojkk.podium.data.rss.createDefaultRssParser()
                )
            ),
            xyzRankRepository = com.opoojkk.podium.data.repository.XYZRankRepository(
                httpClient = environment.httpClient
            ),
            applePodcastSearchRepository = environment.applePodcastSearchRepository,
            scope = scope
        )
    }
}

/**
 * Remember ImportExportViewModel with proper dependencies.
 */
@Composable
fun rememberImportExportViewModel(environment: PodiumEnvironment): ImportExportViewModel {
    val scope = rememberCoroutineScope()
    return remember(environment) {
        ImportExportViewModel(
            repository = environment.repository,
            scope = scope
        )
    }
}
