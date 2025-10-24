package com.opoojkk.podium

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.navigation.PodiumDestination
import com.opoojkk.podium.presentation.rememberPodiumAppState
import com.opoojkk.podium.ui.components.PlaybackBar
import com.opoojkk.podium.ui.player.PlayerDetailScreen
import com.opoojkk.podium.ui.home.HomeScreen
import com.opoojkk.podium.ui.profile.ProfileScreen
import com.opoojkk.podium.ui.subscriptions.SubscriptionsScreen
import kotlinx.coroutines.launch

@Composable
fun PodiumApp(environment: PodiumEnvironment) {
    val appState = rememberPodiumAppState(environment)
    val controller = appState.controller
    val scope = rememberCoroutineScope()
    val showPlayerDetail = remember { mutableStateOf(false) }

    val homeState by controller.homeState.collectAsState()
    val subscriptionsState by controller.subscriptionsState.collectAsState()
    val profileState by controller.profileState.collectAsState()
    val playbackState by controller.playbackState.collectAsState()

    MaterialTheme {
        Scaffold(
            bottomBar = {
                if (!showPlayerDetail.value) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Playback bar just above the navigation bar
                            PlaybackBar(
                                playbackState = playbackState,
                                onPlayPauseClick = {
                                    if (playbackState.isPlaying) {
                                        controller.pause()
                                    } else {
                                        controller.resume()
                                    }
                                },
                                onBarClick = { showPlayerDetail.value = true },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )

                            NavigationBar {
                                PodiumDestination.entries.forEach { destination ->
                                    val selected = destination == appState.currentDestination
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { appState.navigateTo(destination) },
                                        icon = { Icon(imageVector = destination.icon, contentDescription = destination.label) },
                                        label = { Text(destination.label) },
                                    )
                                }
                            }
                        }
                } else null
            },
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    if (showPlayerDetail.value && playbackState.episode != null) {
                        PlayerDetailScreen(
                            playbackState = playbackState,
                            onBack = { showPlayerDetail.value = false },
                            onPlayPause = {
                                if (playbackState.isPlaying) controller.pause() else controller.resume()
                            },
                            onSeekTo = { controller.seekTo(it) },
                            onSeekBack = { controller.seekBy(-15_000) },
                            onSeekForward = { controller.seekBy(30_000) },
                        )
                    } else {
                        when (appState.currentDestination) {
                            PodiumDestination.Home -> HomeScreen(
                                state = homeState,
                                onPlayEpisode = controller::playEpisode,
                            )

                            PodiumDestination.Subscriptions -> SubscriptionsScreen(
                                state = subscriptionsState,
                                onRefresh = controller::refreshSubscriptions,
                                onAddSubscription = controller::subscribe,
                                onEditSubscription = controller::renameSubscription,
                                onDeleteSubscription = controller::deleteSubscription,
                            )

                            PodiumDestination.Profile -> ProfileScreen(
                                state = profileState,
                                onImportClick = {
                                    // In a production app this would open a file picker and pass the OPML content.
                                },
                                onExportClick = {
                                    scope.launch {
                                        val opml = controller.exportOpml()
                                        println("Exported OPML:\n$opml")
                                    }
                                },
                                onToggleAutoDownload = controller::toggleAutoDownload,
                                onManageDownloads = controller::refreshSubscriptions,
                            )
                        }
                    }
                }
            }
        }
    }
}
