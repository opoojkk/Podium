package com.opoojkk.podium

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.navigation.PodiumDestination
import com.opoojkk.podium.presentation.rememberPodiumAppState
import com.opoojkk.podium.ui.components.PlaybackBar
import com.opoojkk.podium.ui.home.HomeScreen
import com.opoojkk.podium.ui.profile.ProfileScreen
import com.opoojkk.podium.ui.subscriptions.SubscriptionsScreen
import kotlinx.coroutines.launch

@Composable
fun PodiumApp(environment: PodiumEnvironment) {
    val appState = rememberPodiumAppState(environment)
    val controller = appState.controller
    val scope = rememberCoroutineScope()

    val homeState by controller.homeState.collectAsState()
    val subscriptionsState by controller.subscriptionsState.collectAsState()
    val profileState by controller.profileState.collectAsState()
    val playbackState by controller.playbackState.collectAsState()

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    PodiumDestination.entries.forEach { destination ->
                        val selected = destination == appState.currentDestination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { appState.navigateTo(destination) },
                            icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).padding(paddingValues)) {
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
                
                // Playback bar at the bottom
                PlaybackBar(
                    playbackState = playbackState,
                    onPlayPauseClick = {
                        if (playbackState.isPlaying) {
                            controller.pause()
                        } else {
                            controller.resume()
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = paddingValues.calculateBottomPadding())
                )
            }
        }
    }
}
