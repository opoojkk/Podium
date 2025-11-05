package com.opoojkk.podium

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.navigation.PodiumDestination
import com.opoojkk.podium.presentation.rememberPodiumAppState
import com.opoojkk.podium.platform.SetStatusBarColor
import com.opoojkk.podium.platform.copyTextToClipboard
import com.opoojkk.podium.platform.openUrl
import com.opoojkk.podium.ui.components.DesktopNavigationRail
import com.opoojkk.podium.ui.components.DesktopPlaybackBar
import com.opoojkk.podium.ui.components.PlaybackBar
import com.opoojkk.podium.ui.components.SleepTimerDialog
import com.opoojkk.podium.ui.home.HomeScreen
import com.opoojkk.podium.ui.home.ViewMoreScreen
import com.opoojkk.podium.ui.player.DesktopPlayerDetailScreen
import com.opoojkk.podium.ui.player.PlayerDetailScreen
import com.opoojkk.podium.ui.player.PlaybackSpeedDialog
import com.opoojkk.podium.ui.profile.ProfileScreen
import com.opoojkk.podium.ui.profile.CacheManagementScreen
import com.opoojkk.podium.ui.profile.ExportOpmlDialog
import com.opoojkk.podium.ui.profile.ImportOpmlDialog
import com.opoojkk.podium.ui.profile.AboutDialog
import com.opoojkk.podium.ui.profile.UpdateIntervalDialog
import com.opoojkk.podium.ui.subscriptions.SubscriptionsScreen
import com.opoojkk.podium.ui.subscriptions.PodcastEpisodesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 查看更多页面类型
enum class ViewMoreType {
    RECENT_PLAYED,
    RECENT_UPDATES,
}

private const val PLAYBACK_COMPLETION_THRESHOLD_MS = 5_000L

private fun PlaybackState.isEpisodeNearCompletion(): Boolean {
    val currentEpisode = episode ?: return false
    val duration = (durationMs ?: currentEpisode.duration)?.takeIf { it > 0 } ?: return false
    val remaining = duration - positionMs
    return remaining <= PLAYBACK_COMPLETION_THRESHOLD_MS || positionMs >= duration
}

@Composable
fun PodiumApp(
    environment: PodiumEnvironment,
    showPlayerDetailFromNotification: androidx.compose.runtime.MutableState<Boolean>? = null,
    onExitApp: (() -> Unit)? = null
) {
    val appState = rememberPodiumAppState(environment)
    val controller = appState.controller
    val scope = rememberCoroutineScope()
    val showPlayerDetail = remember { mutableStateOf(false) }

    // 设置睡眠定时器完成回调
    DisposableEffect(controller) {
        controller.onSleepTimerComplete = {
            onExitApp?.invoke()
        }
        onDispose {
            controller.onSleepTimerComplete = null
        }
    }

    // 监听从通知栏打开的请求
    LaunchedEffect(showPlayerDetailFromNotification?.value) {
        if (showPlayerDetailFromNotification?.value == true) {
            println("🎵 PodiumApp: 收到从通知栏打开播放详情页的请求")
            showPlayerDetail.value = true
            showPlayerDetailFromNotification.value = false // 重置标志
        }
    }
    val showPlaylist = remember { mutableStateOf(false) }
    val showPlaylistFromPlayerDetail = remember { mutableStateOf(false) }
    val showViewMore = remember { mutableStateOf<ViewMoreType?>(null) }
    val selectedPodcast = remember { mutableStateOf<com.opoojkk.podium.data.model.Podcast?>(null) }
    val showCacheManagement = remember { mutableStateOf(false) }
    val showImportDialog = remember { mutableStateOf(false) }
    val showExportDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }
    val showUpdateIntervalDialog = remember { mutableStateOf(false) }
    val importText = remember { mutableStateOf("") }
    val importInProgress = remember { mutableStateOf(false) }
    val importResultState = remember { mutableStateOf<com.opoojkk.podium.data.repository.PodcastRepository.OpmlImportResult?>(null) }
    val importErrorMessage = remember { mutableStateOf<String?>(null) }
    val exportInProgress = remember { mutableStateOf(false) }
    val exportContent = remember { mutableStateOf<String?>(null) }
    val exportErrorMessage = remember { mutableStateOf<String?>(null) }
    val exportFormat = remember { mutableStateOf(com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML) }

    val platformContext = remember { environment.platformContext }
    val fileOperations = remember { environment.fileOperations }

    // Snackbar state for showing notifications
    val snackbarHostState = remember { SnackbarHostState() }

    val homeState by controller.homeState.collectAsState()
    val subscriptionsState by controller.subscriptionsState.collectAsState()
    val profileState by controller.profileState.collectAsState()
    val playlistState by controller.playlistState.collectAsState()
    val playbackState by controller.playbackState.collectAsState()
    val sleepTimerState by controller.sleepTimerState.collectAsState()
    val allRecentListening by controller.allRecentListening.collectAsState(emptyList())
    val allRecentUpdates by controller.allRecentUpdates.collectAsState(emptyList())
    val downloads by controller.downloads.collectAsState()

    // 睡眠定时器对话框状态
    val showSleepTimerDialog = remember { mutableStateOf(false) }

    // 倍速选择对话框状态
    val showSpeedDialog = remember { mutableStateOf(false) }

    val loadExportContent: () -> Unit = {
        exportInProgress.value = true
        exportErrorMessage.value = null
        exportContent.value = null
        scope.launch {
            val result = runCatching { controller.exportSubscriptions(exportFormat.value) }
            result.onSuccess { content ->
                exportContent.value = content
            }.onFailure { throwable ->
                exportContent.value = null
                exportErrorMessage.value = throwable.message ?: "导出失败，请稍后重试。"
            }
            exportInProgress.value = false
        }
    }

    val handleImportClick = {
        showImportDialog.value = true
        importText.value = ""
        importResultState.value = null
        importErrorMessage.value = null
        importInProgress.value = false
    }

    val handleExportClick = {
        showExportDialog.value = true
        exportFormat.value = com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML
        loadExportContent()
    }

    val handleFormatChange: (com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat) -> Unit = { format ->
        exportFormat.value = format
        loadExportContent()
    }

    val handlePickFile: () -> Unit = {
        scope.launch {
            val content = fileOperations.pickFileToImport()
            if (content != null) {
                importText.value = content
            }
        }
    }

    val handleSaveToFile: (String) -> Unit = { content ->
        scope.launch {
            val fileName = when (exportFormat.value) {
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML -> "podium_subscriptions.opml"
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.JSON -> "podium_subscriptions.json"
            }
            val mimeType = when (exportFormat.value) {
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML -> "text/xml"
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.JSON -> "application/json"
            }
            val success = fileOperations.saveToFile(content, fileName, mimeType)
            if (success) {
                println("✅ File saved successfully")
            } else {
                println("❌ Failed to save file")
            }
        }
    }

    val handleImportConfirm: () -> Unit = {
        val content = importText.value.trim()
        if (content.isNotEmpty() && !importInProgress.value) {
            importInProgress.value = true
            importResultState.value = null
            importErrorMessage.value = null
            scope.launch {
                val result = runCatching { controller.importSubscriptions(content) }
                result.onSuccess { importResultState.value = it }
                    .onFailure { throwable ->
                        importErrorMessage.value = throwable.message ?: "导入失败，请稍后重试。"
                    }
                importInProgress.value = false
            }
        }
    }

    val handleImportDismiss: () -> Unit = {
        showImportDialog.value = false
        importText.value = ""
        importResultState.value = null
        importErrorMessage.value = null
        importInProgress.value = false
    }

    val handleExportDismiss: () -> Unit = {
        showExportDialog.value = false
        exportContent.value = null
        exportErrorMessage.value = null
        exportInProgress.value = false
    }

    val copyOpmlToClipboard: (String) -> Boolean = remember(platformContext) {
        { text -> copyTextToClipboard(platformContext, text) }
    }

    val openUrlInBrowser: (String) -> Boolean = remember(platformContext) {
        { url -> openUrl(platformContext, url) }
    }

    var pendingEpisodeId by remember { mutableStateOf<String?>(null) }
    var previousPlaybackState by remember { mutableStateOf(playbackState) }
    var lastHandledCompletionId by remember { mutableStateOf<String?>(null) }

    val playEpisode: (Episode) -> Unit = remember(controller) {
        { episode ->
            pendingEpisodeId = episode.id
            controller.playEpisode(episode)
        }
    }

    LaunchedEffect(playbackState) {
        val previousState = previousPlaybackState
        val currentState = playbackState
        val currentEpisodeId = currentState.episode?.id

        if (currentEpisodeId != null && currentEpisodeId == pendingEpisodeId) {
            pendingEpisodeId = null
        }

        val previousEpisode = previousState.episode
        val episodeCompleted = if (
            previousEpisode != null &&
            currentState.episode?.id != previousEpisode.id &&
            pendingEpisodeId == null &&
            previousState.isEpisodeNearCompletion()
        ) {
            previousEpisode
        } else {
            null
        }

        val completedEpisodeId = episodeCompleted?.id
        if (completedEpisodeId != null && completedEpisodeId != lastHandledCompletionId) {
            lastHandledCompletionId = completedEpisodeId

            controller.markEpisodeCompleted(completedEpisodeId)
            controller.removeFromPlaylist(completedEpisodeId)

            val nextEpisode = playlistState.items
                .asSequence()
                .map { it.episode }
                .firstOrNull { it.id != completedEpisodeId }

            if (nextEpisode != null) {
                playEpisode(nextEpisode)
            } else {
                if (showPlayerDetail.value) {
                    showPlayerDetail.value = false
                    showPlaylistFromPlayerDetail.value = false
                }
                if (appState.currentDestination != PodiumDestination.Home) {
                    appState.navigateTo(PodiumDestination.Home)
                }
            }
        }

        if (currentEpisodeId != null) {
            lastHandledCompletionId = null
        }

        previousPlaybackState = currentState
    }

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
        val colorScheme = MaterialTheme.colorScheme
        val statusBarColor = colorScheme.background
        val useDarkIcons = statusBarColor.luminance() > 0.5f
        SetStatusBarColor(statusBarColor, darkIcons = useDarkIcons)

        // About Dialog
        if (showAboutDialog.value) {
            AboutDialog(
                onDismiss = { showAboutDialog.value = false },
                onOpenUrl = openUrlInBrowser
            )
        }

        // Update Interval Dialog
        if (showUpdateIntervalDialog.value) {
            UpdateIntervalDialog(
                currentInterval = profileState.updateInterval,
                onIntervalSelected = { interval ->
                    controller.setUpdateInterval(interval)
                    showUpdateIntervalDialog.value = false
                },
                onDismiss = { showUpdateIntervalDialog.value = false }
            )
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog.value) {
            SleepTimerDialog(
                sleepTimerState = sleepTimerState,
                onDurationSelected = { duration ->
                    controller.startSleepTimer(duration)
                },
                onCancel = {
                    controller.cancelSleepTimer()
                },
                onDismiss = { showSleepTimerDialog.value = false }
            )
        }

        if (showSpeedDialog.value) {
            PlaybackSpeedDialog(
                currentSpeed = playbackState.playbackSpeed,
                onSpeedSelected = { speed ->
                    controller.setPlaybackSpeed(speed)
                },
                onDismiss = { showSpeedDialog.value = false }
            )
        }

        if (isDesktop) {
            // 桌面平台：使用Spotify风格布局（侧边导航 + 底部播放控制器）
            DesktopLayout(
                appState = appState,
                controller = controller,
                showPlayerDetail = showPlayerDetail,
                showPlaylist = showPlaylist,
                showPlaylistFromPlayerDetail = showPlaylistFromPlayerDetail,
                showViewMore = showViewMore,
                selectedPodcast = selectedPodcast,
                showCacheManagement = showCacheManagement,
                showAboutDialog = showAboutDialog,
                showUpdateIntervalDialog = showUpdateIntervalDialog,
                homeState = homeState,
                subscriptionsState = subscriptionsState,
                profileState = profileState,
                playlistState = playlistState,
                playbackState = playbackState,
                allRecentListening = allRecentListening,
                allRecentUpdates = allRecentUpdates,
                downloads = downloads,
                onImportClick = handleImportClick,
                onExportClick = handleExportClick,
                onPlayEpisode = playEpisode,
                onOpenUrl = openUrlInBrowser,
                snackbarHostState = snackbarHostState,
            )
        } else {
            // 移动平台：使用传统底部导航栏布局
            MobileLayout(
                appState = appState,
                controller = controller,
                showPlayerDetail = showPlayerDetail,
                showPlaylist = showPlaylist,
                showPlaylistFromPlayerDetail = showPlaylistFromPlayerDetail,
                showViewMore = showViewMore,
                selectedPodcast = selectedPodcast,
                showCacheManagement = showCacheManagement,
                showAboutDialog = showAboutDialog,
                showUpdateIntervalDialog = showUpdateIntervalDialog,
                homeState = homeState,
                subscriptionsState = subscriptionsState,
                profileState = profileState,
                playlistState = playlistState,
                playbackState = playbackState,
                sleepTimerState = sleepTimerState,
                showSleepTimerDialog = showSleepTimerDialog,
                showSpeedDialog = showSpeedDialog,
                allRecentListening = allRecentListening,
                allRecentUpdates = allRecentUpdates,
                downloads = downloads,
                onImportClick = handleImportClick,
                onExportClick = handleExportClick,
                onPlayEpisode = playEpisode,
                onOpenUrl = openUrlInBrowser,
                snackbarHostState = snackbarHostState,
            )
        }

        if (showImportDialog.value) {
            ImportOpmlDialog(
                opmlText = importText.value,
                onOpmlTextChange = { importText.value = it },
                isProcessing = importInProgress.value,
                result = importResultState.value,
                errorMessage = importErrorMessage.value,
                onConfirm = handleImportConfirm,
                onDismiss = handleImportDismiss,
                onPickFile = handlePickFile,
            )
        }
        if (showExportDialog.value) {
            ExportOpmlDialog(
                isProcessing = exportInProgress.value,
                opmlContent = exportContent.value,
                errorMessage = exportErrorMessage.value,
                selectedFormat = exportFormat.value,
                onFormatChange = handleFormatChange,
                onRetry = loadExportContent,
                onDismiss = handleExportDismiss,
                onCopy = copyOpmlToClipboard,
                onSaveToFile = handleSaveToFile,
            )
        }
    }
}

@Composable
private fun DesktopLayout(
    appState: com.opoojkk.podium.presentation.PodiumAppState,
    controller: com.opoojkk.podium.presentation.PodiumController,
    showPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showPlaylistFromPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showViewMore: androidx.compose.runtime.MutableState<ViewMoreType?>,
    selectedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.Podcast?>,
    showCacheManagement: androidx.compose.runtime.MutableState<Boolean>,
    showAboutDialog: androidx.compose.runtime.MutableState<Boolean>,
    showUpdateIntervalDialog: androidx.compose.runtime.MutableState<Boolean>,
    homeState: com.opoojkk.podium.presentation.HomeUiState,
    subscriptionsState: com.opoojkk.podium.presentation.SubscriptionsUiState,
    profileState: com.opoojkk.podium.presentation.ProfileUiState,
    playlistState: com.opoojkk.podium.presentation.PlaylistUiState,
    playbackState: com.opoojkk.podium.data.model.PlaybackState,
    allRecentListening: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    allRecentUpdates: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    downloads: Map<String, com.opoojkk.podium.data.model.DownloadStatus>,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onOpenUrl: (String) -> Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    // 侧边栏展开状态
    var isNavigationExpanded by remember { mutableStateOf(true) }

    // Material3 风格的背景
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                // 左侧导航栏 - 始终显示
                DesktopNavigationRail(
                    currentDestination = appState.currentDestination,
                    onNavigate = { appState.navigateTo(it) },
                    isExpanded = isNavigationExpanded,
                    onToggleExpand = { isNavigationExpanded = !isNavigationExpanded }
                )

                // 主内容区域 - 播放列表从右侧滑入
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    // 主内容区域 - 始终显示
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
                                onPlaylistClick = {
                                    showPlayerDetail.value = false
                                    showPlaylistFromPlayerDetail.value = true
                                    showPlaylist.value = true
                                },
                            )
                        }
                        selectedPodcast.value != null -> {
                            // 显示播客单集列表
                            val podcast = selectedPodcast.value!!
                            val podcastEpisodes by controller.getPodcastEpisodes(podcast.id).collectAsState(emptyList())
                            PodcastEpisodesScreen(
                                podcast = podcast,
                                episodes = podcastEpisodes,
                                onPlayEpisode = onPlayEpisode,
                                onBack = { selectedPodcast.value = null },
                                downloads = downloads,
                                onDownloadEpisode = controller::enqueueDownload,
                                onRefresh = { onComplete ->
                                    controller.refreshPodcast(podcast.id, onComplete)
                                },
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
                                onPlayEpisode = onPlayEpisode,
                                onBack = { showViewMore.value = null },
                            )
                        }
                        else -> {
                            when (appState.currentDestination) {
                                PodiumDestination.Home -> HomeScreen(
                                    state = homeState,
                                    onPlayEpisode = onPlayEpisode,
                                    onSearchQueryChange = controller::onHomeSearchQueryChange,
                                    onClearSearch = controller::clearHomeSearch,
                                    onViewMoreRecentPlayed = { showViewMore.value = ViewMoreType.RECENT_PLAYED },
                                    onViewMoreRecentUpdates = { showViewMore.value = ViewMoreType.RECENT_UPDATES },
                                    onRefresh = {
                                        controller.refreshSubscriptions { count ->
                                            scope.launch {
                                                val message = if (count > 0) {
                                                    "更新完成，发现 $count 个新节目"
                                                } else {
                                                    "已是最新"
                                                }
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        }
                                    },
                                    isRefreshing = subscriptionsState.isRefreshing,
                                )

                                PodiumDestination.Subscriptions -> SubscriptionsScreen(
                                    state = subscriptionsState,
                                    onRefresh = controller::refreshSubscriptions,
                                    onAddSubscription = controller::subscribe,
                                    onEditSubscription = controller::renameSubscription,
                                    onDeleteSubscription = controller::deleteSubscription,
                                    onPodcastClick = { podcast -> selectedPodcast.value = podcast },
                                    onClearDuplicateMessage = controller::clearDuplicateSubscriptionMessage,
                                )

                                PodiumDestination.Profile -> ProfileScreen(
                                    state = profileState,
                                    onImportClick = onImportClick,
                                    onExportClick = onExportClick,
                                    onCacheManagementClick = { showCacheManagement.value = true },
                                    onAboutClick = { showAboutDialog.value = true },
                                    onUpdateIntervalClick = { showUpdateIntervalDialog.value = true },
                                )
                            }
                        }
                    }

                    // 播放列表从右侧滑入覆盖主内容
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPlaylist.value,
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 150,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        ) + androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 200,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 100,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        ) + androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 150,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        )
                    ) {
                        com.opoojkk.podium.ui.playlist.PlaylistScreen(
                            state = playlistState,
                            onPlayEpisode = { episode ->
                                onPlayEpisode(episode)
                                showPlaylist.value = false
                                if (showPlaylistFromPlayerDetail.value) {
                                    showPlayerDetail.value = true
                                    showPlaylistFromPlayerDetail.value = false
                                }
                            },
                            onMarkCompleted = { episodeId ->
                                controller.markEpisodeCompleted(episodeId)
                            },
                            onRemoveFromPlaylist = { episodeId ->
                                controller.removeFromPlaylist(episodeId)
                            },
                            onBack = {
                                showPlaylist.value = false
                                if (showPlaylistFromPlayerDetail.value) {
                                    showPlayerDetail.value = true
                                    showPlaylistFromPlayerDetail.value = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // 底部播放控制器 - 播放详情展开时隐藏，带动画
            androidx.compose.animation.AnimatedVisibility(
                visible = !showPlayerDetail.value,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 250,
                        easing = androidx.compose.animation.core.LinearOutSlowInEasing
                    )
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 300,
                        easing = androidx.compose.animation.core.LinearOutSlowInEasing
                    )
                ),
                exit = androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 200,
                        easing = androidx.compose.animation.core.FastOutLinearInEasing
                    )
                ) + androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 250,
                        easing = androidx.compose.animation.core.FastOutLinearInEasing
                    )
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

        // Snackbar host for showing notifications
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp),
            snackbar = { snackbarData ->
                androidx.compose.material3.Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        )
        }
    }
}

@Composable
private fun MobileLayout(
    appState: com.opoojkk.podium.presentation.PodiumAppState,
    controller: com.opoojkk.podium.presentation.PodiumController,
    showPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showPlaylist: androidx.compose.runtime.MutableState<Boolean>,
    showPlaylistFromPlayerDetail: androidx.compose.runtime.MutableState<Boolean>,
    showViewMore: androidx.compose.runtime.MutableState<ViewMoreType?>,
    selectedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.Podcast?>,
    showCacheManagement: androidx.compose.runtime.MutableState<Boolean>,
    showAboutDialog: androidx.compose.runtime.MutableState<Boolean>,
    showUpdateIntervalDialog: androidx.compose.runtime.MutableState<Boolean>,
    homeState: com.opoojkk.podium.presentation.HomeUiState,
    subscriptionsState: com.opoojkk.podium.presentation.SubscriptionsUiState,
    profileState: com.opoojkk.podium.presentation.ProfileUiState,
    playlistState: com.opoojkk.podium.presentation.PlaylistUiState,
    playbackState: com.opoojkk.podium.data.model.PlaybackState,
    sleepTimerState: com.opoojkk.podium.data.model.SleepTimerState,
    showSleepTimerDialog: MutableState<Boolean>,
    showSpeedDialog: MutableState<Boolean>,
    allRecentListening: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    allRecentUpdates: List<com.opoojkk.podium.data.model.EpisodeWithPodcast>,
    downloads: Map<String, com.opoojkk.podium.data.model.DownloadStatus>,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onOpenUrl: (String) -> Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容区域
        Scaffold(
            bottomBar = {
                // 底部栏带动画显示/隐藏
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showPlayerDetail.value && !showPlaylist.value && showViewMore.value == null && !showCacheManagement.value,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 250,
                            easing = androidx.compose.animation.core.LinearOutSlowInEasing
                        )
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 300,
                            easing = androidx.compose.animation.core.LinearOutSlowInEasing
                        )
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 200,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        )
                    ) + androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 250,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        )
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
            Box(modifier = Modifier.fillMaxSize()) {
                // 主内容区域
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
                            onPlayEpisode = onPlayEpisode,
                            onBack = { selectedPodcast.value = null },
                            downloads = downloads,
                            onDownloadEpisode = controller::enqueueDownload,
                            onRefresh = { onComplete ->
                                controller.refreshPodcast(podcast.id, onComplete)
                            },
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
                            onPlayEpisode = onPlayEpisode,
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
                                    onPlayEpisode = onPlayEpisode,
                                    onSearchQueryChange = controller::onHomeSearchQueryChange,
                                    onClearSearch = controller::clearHomeSearch,
                                    onViewMoreRecentPlayed = { showViewMore.value = ViewMoreType.RECENT_PLAYED },
                                    onViewMoreRecentUpdates = { showViewMore.value = ViewMoreType.RECENT_UPDATES },
                                    onRefresh = {
                                        controller.refreshSubscriptions { count ->
                                            scope.launch {
                                                val message = if (count > 0) {
                                                    "更新完成，发现 $count 个新节目"
                                                } else {
                                                    "已是最新"
                                                }
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        }
                                    },
                                    isRefreshing = subscriptionsState.isRefreshing,
                                )

                                PodiumDestination.Subscriptions -> SubscriptionsScreen(
                                    state = subscriptionsState,
                                    onRefresh = controller::refreshSubscriptions,
                                    onAddSubscription = controller::subscribe,
                                    onEditSubscription = controller::renameSubscription,
                                    onDeleteSubscription = controller::deleteSubscription,
                                    onPodcastClick = { podcast -> selectedPodcast.value = podcast },
                                    onClearDuplicateMessage = controller::clearDuplicateSubscriptionMessage,
                                )

                                PodiumDestination.Profile -> ProfileScreen(
                                    state = profileState,
                                    onImportClick = onImportClick,
                                    onExportClick = onExportClick,
                                    onCacheManagementClick = { showCacheManagement.value = true },
                                    onAboutClick = { showAboutDialog.value = true },
                                    onUpdateIntervalClick = { showUpdateIntervalDialog.value = true },
                                )
                            }
                        }
                    }
                }

                // 播放列表从上往下滑入覆盖主内容
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPlaylist.value,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 150,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        )
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 200,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        )
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 100,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        )
                    ) + androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 150,
                            easing = androidx.compose.animation.core.FastOutLinearInEasing
                        )
                    )
                ) {
                    com.opoojkk.podium.ui.playlist.PlaylistScreen(
                        state = playlistState,
                        onPlayEpisode = { episode ->
                            onPlayEpisode(episode)
                            showPlaylist.value = false
                            if (showPlaylistFromPlayerDetail.value) {
                                showPlayerDetail.value = true
                                showPlaylistFromPlayerDetail.value = false
                            }
                        },
                        onMarkCompleted = { episodeId ->
                            controller.markEpisodeCompleted(episodeId)
                        },
                        onRemoveFromPlaylist = { episodeId ->
                            controller.removeFromPlaylist(episodeId)
                        },
                        onBack = {
                            showPlaylist.value = false
                            if (showPlaylistFromPlayerDetail.value) {
                                showPlayerDetail.value = true
                                showPlaylistFromPlayerDetail.value = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // 播放详情页 - 全屏覆盖，带动画
        androidx.compose.animation.AnimatedVisibility(
            visible = showPlayerDetail.value && playbackState.episode != null,
            enter = androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 350,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 350,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ),
            exit = androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 250,
                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                )
            ) + androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 250,
                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                )
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
                onPlaylistClick = {
                    showPlayerDetail.value = false
                    showPlaylistFromPlayerDetail.value = true
                    showPlaylist.value = true
                },
                downloadStatus = playbackState.episode?.let { downloads[it.id] },
                onDownloadClick = {
                    playbackState.episode?.let { controller.enqueueDownload(it) }
                },
                playbackSpeed = playbackState.playbackSpeed,
                onSpeedChange = { showSpeedDialog.value = true },
                sleepTimerMinutes = if (sleepTimerState.isActive) sleepTimerState.remainingMinutes else null,
                onSleepTimerClick = { showSleepTimerDialog.value = true },
            )
        }

        // Snackbar host for showing notifications
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp),
            snackbar = { snackbarData ->
                androidx.compose.material3.Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        )
    }
}
