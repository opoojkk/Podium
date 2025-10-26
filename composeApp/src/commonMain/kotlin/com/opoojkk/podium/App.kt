package com.opoojkk.podium

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.navigation.PodiumDestination
import com.opoojkk.podium.presentation.rememberPodiumAppState
import com.opoojkk.podium.ui.components.DesktopNavigationRail
import com.opoojkk.podium.ui.components.DesktopPlaybackBar
import com.opoojkk.podium.ui.components.PlaybackBar
import com.opoojkk.podium.ui.home.HomeScreen
import com.opoojkk.podium.ui.home.ViewMoreScreen
import com.opoojkk.podium.ui.player.DesktopPlayerDetailScreen
import com.opoojkk.podium.ui.player.PlayerDetailScreen
import com.opoojkk.podium.ui.profile.ProfileScreen
import com.opoojkk.podium.ui.profile.CacheManagementScreen
import com.opoojkk.podium.ui.subscriptions.SubscriptionsScreen
import com.opoojkk.podium.ui.subscriptions.PodcastEpisodesScreen
import kotlinx.coroutines.launch

// 查看更多页面类型
enum class ViewMoreType {
    RECENT_PLAYED,
    RECENT_UPDATES,
}

@Composable
fun PodiumApp(environment: PodiumEnvironment) {
    val appState = rememberPodiumAppState(environment)
    val controller = appState.controller
    val scope = rememberCoroutineScope()
    val showPlayerDetail = remember { mutableStateOf(false) }
    val showViewMore = remember { mutableStateOf<ViewMoreType?>(null) }
    val selectedPodcast = remember { mutableStateOf<com.opoojkk.podium.data.model.Podcast?>(null) }
    val showCacheManagement = remember { mutableStateOf(false) }

    val homeState by controller.homeState.collectAsState()
    val subscriptionsState by controller.subscriptionsState.collectAsState()
    val profileState by controller.profileState.collectAsState()
    val playbackState by controller.playbackState.collectAsState()
    val allRecentListening by controller.allRecentListening.collectAsState(emptyList())
    val allRecentUpdates by controller.allRecentUpdates.collectAsState(emptyList())

    // 检测当前平台
    val platform = remember { getPlatform() }
    val isDesktop = remember(platform) {
        val isDesktopPlatform = platform.name.contains("JVM", ignoreCase = true) ||
                platform.name.contains("Desktop", ignoreCase = true) ||
                platform.name.contains("Java", ignoreCase = true)
        println("🖥️ Platform detected: ${platform.name}")
        println("🎨 Using ${if (isDesktopPlatform) "Desktop" else "Mobile"} Layout")
        isDesktopPlatform
    }

    MaterialTheme {
        if (isDesktop) {
            // 桌面平台：使用Spotify风格布局（侧边导航 + 底部播放控制器）
            DesktopLayout(
                appState = appState,
                controller = controller,
                scope = scope,
                showPlayerDetail = showPlayerDetail,
                showViewMore = showViewMore,
                selectedPodcast = selectedPodcast,
                showCacheManagement = showCacheManagement,
                homeState = homeState,
                subscriptionsState = subscriptionsState,
                profileState = profileState,
                playbackState = playbackState,
                allRecentListening = allRecentListening,
                allRecentUpdates = allRecentUpdates
            )
        } else {
            // 移动平台：使用传统底部导航栏布局
            MobileLayout(
                appState = appState,
                controller = controller,
                scope = scope,
                showPlayerDetail = showPlayerDetail,
                showViewMore = showViewMore,
                selectedPodcast = selectedPodcast,
                showCacheManagement = showCacheManagement,
                homeState = homeState,
                subscriptionsState = subscriptionsState,
                profileState = profileState,
                playbackState = playbackState,
                allRecentListening = allRecentListening,
                allRecentUpdates = allRecentUpdates
            )
        }
    }
}

@Composable
private fun DesktopLayout(
    appState: com.opoojkk.podium.presentation.PodiumAppState,
    controller: com.opoojkk.podium.presentation.PodiumController,
    scope: kotlinx.coroutines.CoroutineScope,
    showPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showViewMore: androidx.compose.runtime.MutableState<ViewMoreType?>,
    selectedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.Podcast?>,
    showCacheManagement: androidx.compose.runtime.MutableState<Boolean>,
    homeState: com.opoojkk.podium.presentation.HomeUiState,
    subscriptionsState: com.opoojkk.podium.presentation.SubscriptionsUiState,
    profileState: com.opoojkk.podium.presentation.ProfileUiState,
    playbackState: com.opoojkk.podium.data.model.PlaybackState,
    allRecentListening: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    allRecentUpdates: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
) {
    // 侧边栏展开状态
    var isNavigationExpanded by remember { mutableStateOf(true) }

    // Material3 风格的背景
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                // 左侧导航栏 - 始终显示
                DesktopNavigationRail(
                    currentDestination = appState.currentDestination,
                    onNavigate = { appState.navigateTo(it) },
                    isExpanded = isNavigationExpanded,
                    onToggleExpand = { isNavigationExpanded = !isNavigationExpanded }
                )

                // 主内容区域 - 直接切换，避免动画导致的跳动
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    when {
                        showCacheManagement.value -> {
                            // 显示缓存管理页面
                            CacheManagementScreen(
                                state = profileState,
                                onBackClick = { showCacheManagement.value = false },
                                onTogglePodcastAutoDownload = controller::togglePodcastAutoDownload,
                                onClearCache = { /* TODO: 实现清除缓存功能 */ },
                            )
                        }
                        showPlayerDetail.value && playbackState.episode != null -> {
                            // 桌面端使用横向布局的详情页
                            DesktopPlayerDetailScreen(
                                playbackState = playbackState,
                                onBack = { showPlayerDetail.value = false },
                                onPlayPause = {
                                    if (playbackState.isPlaying) controller.pause() else controller.resume()
                                },
                                onSeekTo = { controller.seekTo(it) },
                                onSeekBack = { controller.seekBy(-15_000) },
                                onSeekForward = { controller.seekBy(30_000) },
                            )
                        }
                        selectedPodcast.value != null -> {
                            // 显示播客单集列表
                            val podcast = selectedPodcast.value!!
                            val podcastEpisodes by controller.getPodcastEpisodes(podcast.id).collectAsState(emptyList())
                            PodcastEpisodesScreen(
                                podcast = podcast,
                                episodes = podcastEpisodes,
                                onPlayEpisode = controller::playEpisode,
                                onBack = { selectedPodcast.value = null },
                            )
                        }
                        showViewMore.value != null -> {
                            // 显示查看更多页面
                            val viewMoreType = showViewMore.value!!
                            val (title, episodes) = when (viewMoreType) {
                                ViewMoreType.RECENT_PLAYED -> "最近收听" to allRecentListening
                                ViewMoreType.RECENT_UPDATES -> "最近更新" to allRecentUpdates
                            }
                            ViewMoreScreen(
                                title = title,
                                episodes = episodes,
                                onPlayEpisode = controller::playEpisode,
                                onBack = { showViewMore.value = null },
                            )
                        }
                        else -> {
                            when (appState.currentDestination) {
                                PodiumDestination.Home -> HomeScreen(
                                    state = homeState,
                                    onPlayEpisode = controller::playEpisode,
                                    onViewMoreRecentPlayed = { showViewMore.value = ViewMoreType.RECENT_PLAYED },
                                    onViewMoreRecentUpdates = { showViewMore.value = ViewMoreType.RECENT_UPDATES },
                                )

                                PodiumDestination.Subscriptions -> SubscriptionsScreen(
                                    state = subscriptionsState,
                                    onRefresh = controller::refreshSubscriptions,
                                    onAddSubscription = controller::subscribe,
                                    onEditSubscription = controller::renameSubscription,
                                    onDeleteSubscription = controller::deleteSubscription,
                                    onPodcastClick = { podcast -> selectedPodcast.value = podcast },
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
                                    onCacheManagementClick = { showCacheManagement.value = true },
                                )
                            }
                        }
                    }
                }
            }

            // 底部播放控制器 - 播放详情展开时隐藏，带动画
            androidx.compose.animation.AnimatedVisibility(
                visible = !showPlayerDetail.value,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ),
                exit = androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                )
            ) {
                DesktopPlaybackBar(
                    playbackState = playbackState,
                    onPlayPauseClick = {
                        if (playbackState.isPlaying) {
                            controller.pause()
                        } else {
                            controller.resume()
                        }
                    },
                    onSeekBack = { controller.seekBy(-15_000) },
                    onSeekForward = { controller.seekBy(30_000) },
                    onSeekTo = { controller.seekTo(it) },
                    onBarClick = { showPlayerDetail.value = true }
                )
            }
        }
    }
}

@Composable
private fun MobileLayout(
    appState: com.opoojkk.podium.presentation.PodiumAppState,
    controller: com.opoojkk.podium.presentation.PodiumController,
    scope: kotlinx.coroutines.CoroutineScope,
    showPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showViewMore: androidx.compose.runtime.MutableState<ViewMoreType?>,
    selectedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.Podcast?>,
    showCacheManagement: androidx.compose.runtime.MutableState<Boolean>,
    homeState: com.opoojkk.podium.presentation.HomeUiState,
    subscriptionsState: com.opoojkk.podium.presentation.SubscriptionsUiState,
    profileState: com.opoojkk.podium.presentation.ProfileUiState,
    playbackState: com.opoojkk.podium.data.model.PlaybackState,
    allRecentListening: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    allRecentUpdates: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容区域
        Scaffold(
            bottomBar = {
                // 底部栏带动画显示/隐藏
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showPlayerDetail.value && showViewMore.value == null && !showCacheManagement.value,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ) + androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                ) {
                    // 当显示播客单集列表时，隐藏底部栏
                    if (selectedPodcast.value == null) {
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
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            when {
                showCacheManagement.value -> {
                    // 显示缓存管理页面 - 不应用 padding，让 CacheManagementScreen 的 Scaffold 自己处理
                    CacheManagementScreen(
                        state = profileState,
                        onBackClick = { showCacheManagement.value = false },
                        onTogglePodcastAutoDownload = controller::togglePodcastAutoDownload,
                        onClearCache = { /* TODO: 实现清除缓存功能 */ },
                    )
                }
                selectedPodcast.value != null -> {
                    // 显示播客单集列表
                    val podcast = selectedPodcast.value!!
                    val podcastEpisodes by controller.getPodcastEpisodes(podcast.id).collectAsState(emptyList())
                    PodcastEpisodesScreen(
                        podcast = podcast,
                        episodes = podcastEpisodes,
                        onPlayEpisode = controller::playEpisode,
                        onBack = { selectedPodcast.value = null },
                    )
                }
                showViewMore.value != null -> {
                    // 显示查看更多页面 - 不应用 padding，让 Scaffold 自己处理
                    val viewMoreType = showViewMore.value!!
                    val (title, episodes) = when (viewMoreType) {
                        ViewMoreType.RECENT_PLAYED -> "最近收听" to allRecentListening
                        ViewMoreType.RECENT_UPDATES -> "最近更新" to allRecentUpdates
                    }
                    ViewMoreScreen(
                        title = title,
                        episodes = episodes,
                        onPlayEpisode = controller::playEpisode,
                        onBack = { showViewMore.value = null },
                    )
                }

                !showPlayerDetail.value -> {
                    // 不显示详情页时的内容 - 应用 padding
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = paddingValues.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding()
                            )
                    ) {
                        when (appState.currentDestination) {
                            PodiumDestination.Home -> HomeScreen(
                                state = homeState,
                                onPlayEpisode = controller::playEpisode,
                                onViewMoreRecentPlayed = { showViewMore.value = ViewMoreType.RECENT_PLAYED },
                                onViewMoreRecentUpdates = { showViewMore.value = ViewMoreType.RECENT_UPDATES },
                            )

                            PodiumDestination.Subscriptions -> SubscriptionsScreen(
                                state = subscriptionsState,
                                onRefresh = controller::refreshSubscriptions,
                                onAddSubscription = controller::subscribe,
                                onEditSubscription = controller::renameSubscription,
                                onDeleteSubscription = controller::deleteSubscription,
                                onPodcastClick = { podcast -> selectedPodcast.value = podcast },
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
                                onCacheManagementClick = { showCacheManagement.value = true },
                            )
                        }
                    }
                }
            }
        }

        // 播放详情页 - 全屏覆盖，带动画
        androidx.compose.animation.AnimatedVisibility(
            visible = showPlayerDetail.value && playbackState.episode != null,
            enter = androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(300)
            ) + androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            ),
            exit = androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(250)
            ) + androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(300)
            )
        ) {
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
        }
    }
}
