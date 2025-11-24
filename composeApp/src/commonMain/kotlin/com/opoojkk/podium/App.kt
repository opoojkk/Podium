package com.opoojkk.podium

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
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
import com.opoojkk.podium.ui.categories.CategoriesScreen
import com.opoojkk.podium.ui.categories.CategoryDetailScreen
import com.opoojkk.podium.data.repository.RecommendedPodcastRepository
import com.opoojkk.podium.data.repository.XYZRankRepository
import com.opoojkk.podium.data.model.recommended.PodcastCategory
import com.opoojkk.podium.data.mapper.toEpisodeWithPodcast
import com.opoojkk.podium.data.mapper.toPodcast
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
            Logger.d("App") { "收到从通知栏打开播放详情页的请求" }
            showPlayerDetail.value = true
            showPlayerDetailFromNotification.value = false // 重置标志
        }
    }
    val showPlaylist = remember { mutableStateOf(false) }
    val showPlaylistFromPlayerDetail = remember { mutableStateOf(false) }
    val showViewMore = remember { mutableStateOf<ViewMoreType?>(null) }
    val selectedPodcast = remember { mutableStateOf<com.opoojkk.podium.data.model.Podcast?>(null) }
    val selectedCategory = remember { mutableStateOf<PodcastCategory?>(null) }
    val selectedRecommendedPodcast = remember { mutableStateOf<com.opoojkk.podium.data.model.recommended.RecommendedPodcast?>(null) }
    val showRecommendedPodcastDetail = remember { mutableStateOf(false) }
    val showCacheManagement = remember { mutableStateOf(false) }

    // Use HomeViewModel for categories and XYZRank data
    val homeViewModel = com.opoojkk.podium.presentation.viewmodel.rememberHomeViewModel(environment)
    val homeViewModelState by homeViewModel.state.collectAsState()

    // For backward compatibility, create references to ViewModel state
    val categoriesState = remember(homeViewModelState.categories) {
        mutableStateOf(homeViewModelState.categories)
    }
    val categoriesLoading = remember(homeViewModelState.isLoading) {
        mutableStateOf(homeViewModelState.isLoading)
    }
    val hotEpisodes = remember(homeViewModelState.hotEpisodes) {
        mutableStateOf(homeViewModelState.hotEpisodes)
    }
    val hotPodcasts = remember(homeViewModelState.hotPodcasts) {
        mutableStateOf(homeViewModelState.hotPodcasts)
    }
    val newEpisodes = remember(homeViewModelState.newEpisodes) {
        mutableStateOf(homeViewModelState.newEpisodes)
    }
    val newPodcasts = remember(homeViewModelState.newPodcasts) {
        mutableStateOf(homeViewModelState.newPodcasts)
    }

    // RecommendedPodcastRepository for category detail screen
    val recommendedPodcastRepository = remember {
        RecommendedPodcastRepository(
            feedService = com.opoojkk.podium.data.rss.PodcastFeedService(
                httpClient = environment.httpClient,
                parser = com.opoojkk.podium.data.rss.createDefaultRssParser()
            )
        )
    }
    // Use ImportExportViewModel for OPML operations
    val importExportViewModel = com.opoojkk.podium.presentation.viewmodel.rememberImportExportViewModel(environment)
    val importState by importExportViewModel.importState.collectAsState()
    val exportState by importExportViewModel.exportState.collectAsState()

    val showImportDialog = remember { mutableStateOf(false) }
    val showExportDialog = remember { mutableStateOf(false) }
    val showAboutDialog = remember { mutableStateOf(false) }
    val showUpdateIntervalDialog = remember { mutableStateOf(false) }

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

    val handleImportClick = {
        showImportDialog.value = true
        importExportViewModel.resetImport()
    }

    val handleExportClick = {
        showExportDialog.value = true
        importExportViewModel.setExportFormat(com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML)
    }

    val handleFormatChange: (com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat) -> Unit = { format ->
        importExportViewModel.setExportFormat(format)
    }

    val handlePickFile: () -> Unit = {
        scope.launch {
            val content = fileOperations.pickFileToImport()
            if (content != null) {
                importExportViewModel.setImportText(content)
            }
        }
    }

    val handleSaveToFile: (String) -> Unit = { content ->
        scope.launch {
            val fileName = when (exportState.format) {
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML -> "podium_subscriptions.opml"
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.JSON -> "podium_subscriptions.json"
            }
            val mimeType = when (exportState.format) {
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.OPML -> "text/xml"
                com.opoojkk.podium.data.repository.PodcastRepository.ExportFormat.JSON -> "application/json"
            }
            val success = fileOperations.saveToFile(content, fileName, mimeType)
            if (success) {
                Logger.i("App") { "File saved successfully: $fileName" }
            } else {
                Logger.e("App", "Failed to save file: $fileName")
            }
        }
    }

    val handleImportConfirm: () -> Unit = {
        importExportViewModel.startImport()
    }

    val handleImportDismiss: () -> Unit = {
        showImportDialog.value = false
        importExportViewModel.resetImport()
    }

    val handleExportDismiss: () -> Unit = {
        showExportDialog.value = false
        importExportViewModel.resetExport()
    }

    val copyOpmlToClipboard: (String) -> Boolean = remember(platformContext) {
        { text -> copyTextToClipboard(platformContext, text) }
    }

    val openUrlInBrowser: (String) -> Boolean = remember(platformContext) {
        { url -> openUrl(platformContext, url) }
    }

    // Handle XYZRank podcast click using HomeViewModel
    val handleXYZRankPodcastClick: (Podcast) -> Unit = remember(
        homeViewModel,
        openUrlInBrowser,
        scope
    ) {
        { podcast ->
            if (podcast.id.startsWith("xyzrank_podcast_")) {
                scope.launch {
                    val recommendedPodcast = homeViewModel.searchAndConvertXYZRankPodcast(podcast)
                    if (recommendedPodcast != null) {
                        selectedRecommendedPodcast.value = recommendedPodcast
                        showRecommendedPodcastDetail.value = true
                    } else {
                        // Fallback to 小宇宙 web link
                        homeViewModel.extractWebLink(podcast.description)?.let { webLink ->
                            openUrlInBrowser(webLink)
                        }
                    }
                }
            } else if (podcast.id.startsWith("itunes_")) {
                // Handle iTunes search results
                val recommendedPodcast = com.opoojkk.podium.data.model.recommended.RecommendedPodcast(
                    id = podcast.id,
                    name = podcast.title,
                    host = "",
                    description = podcast.description,
                    artworkUrl = podcast.artworkUrl,
                    rssUrl = podcast.feedUrl
                )
                selectedRecommendedPodcast.value = recommendedPodcast
                showRecommendedPodcastDetail.value = true
            } else {
                selectedPodcast.value = podcast
            }
        }
    }

    var pendingEpisodeId by remember { mutableStateOf<String?>(null) }
    var previousPlaybackState by remember { mutableStateOf(playbackState) }
    var lastHandledCompletionId by remember { mutableStateOf<String?>(null) }

    val playEpisode: (Episode) -> Unit = remember(controller, homeViewModel, openUrlInBrowser, scope) {
        { episode ->
            // Check if episode is from XYZRank (no audio URL)
            if (episode.audioUrl.isBlank() && episode.id.startsWith("xyzrank_episode_")) {
                scope.launch {
                    val playableEpisode = homeViewModel.searchAndConvertXYZRankEpisode(episode)
                    if (playableEpisode != null) {
                        pendingEpisodeId = playableEpisode.id
                        controller.playEpisode(playableEpisode)
                    } else {
                        // Fallback to 小宇宙 web link
                        homeViewModel.extractWebLink(episode.description)?.let { webLink ->
                            openUrlInBrowser(webLink)
                        }
                    }
                }
            } else {
                // Normal episode, play it
                pendingEpisodeId = episode.id
                controller.playEpisode(episode)
            }
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
        Logger.i("App") { "Platform detected: ${platform.name}" }
        Logger.i("App") { "Using ${if (isDesktopPlatform) "Desktop" else "Mobile"} Layout" }
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
            // 桌面平台：使用左侧导航栏布局，保持与移动平台一致的样式风格
            DesktopLayout(
                appState = appState,
                controller = controller,
                showPlayerDetail = showPlayerDetail,
                showPlaylist = showPlaylist,
                showPlaylistFromPlayerDetail = showPlaylistFromPlayerDetail,
                showViewMore = showViewMore,
                selectedPodcast = selectedPodcast,
                selectedCategory = selectedCategory,
                selectedRecommendedPodcast = selectedRecommendedPodcast,
                showRecommendedPodcastDetail = showRecommendedPodcastDetail,
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
                categories = categoriesState.value,
                categoriesLoading = categoriesLoading.value,
                recommendedPodcastRepository = recommendedPodcastRepository,
                hotEpisodes = hotEpisodes.value,
                hotPodcasts = hotPodcasts.value,
                newEpisodes = newEpisodes.value,
                newPodcasts = newPodcasts.value,
                environment = environment,
                onImportClick = handleImportClick,
                onExportClick = handleExportClick,
                onPlayEpisode = playEpisode,
                onPodcastClick = handleXYZRankPodcastClick,
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
                selectedCategory = selectedCategory,
                selectedRecommendedPodcast = selectedRecommendedPodcast,
                showRecommendedPodcastDetail = showRecommendedPodcastDetail,
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
                categories = categoriesState.value,
                categoriesLoading = categoriesLoading.value,
                recommendedPodcastRepository = recommendedPodcastRepository,
                hotEpisodes = hotEpisodes.value,
                hotPodcasts = hotPodcasts.value,
                newEpisodes = newEpisodes.value,
                newPodcasts = newPodcasts.value,
                environment = environment,
                onImportClick = handleImportClick,
                onExportClick = handleExportClick,
                onPlayEpisode = playEpisode,
                onPodcastClick = handleXYZRankPodcastClick,
                onOpenUrl = openUrlInBrowser,
                snackbarHostState = snackbarHostState,
            )
        }

        if (showImportDialog.value) {
            ImportOpmlDialog(
                opmlText = importState.text,
                onOpmlTextChange = { importExportViewModel.setImportText(it) },
                isProcessing = importState.isProcessing,
                result = importState.result,
                errorMessage = importState.errorMessage,
                onConfirm = handleImportConfirm,
                onDismiss = handleImportDismiss,
                onPickFile = handlePickFile,
            )
        }
        if (showExportDialog.value) {
            ExportOpmlDialog(
                isProcessing = exportState.isProcessing,
                opmlContent = exportState.content,
                errorMessage = exportState.errorMessage,
                selectedFormat = exportState.format,
                onFormatChange = handleFormatChange,
                onRetry = { importExportViewModel.startExport() },
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
    selectedCategory: androidx.compose.runtime.MutableState<PodcastCategory?>,
    selectedRecommendedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.recommended.RecommendedPodcast?>,
    showRecommendedPodcastDetail: androidx.compose.runtime.MutableState<Boolean>,
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
    categories: List<PodcastCategory>,
    categoriesLoading: Boolean,
    recommendedPodcastRepository: RecommendedPodcastRepository,
    hotEpisodes: List<EpisodeWithPodcast>,
    hotPodcasts: List<Podcast>,
    newEpisodes: List<EpisodeWithPodcast>,
    newPodcasts: List<Podcast>,
    environment: PodiumEnvironment,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onOpenUrl: (String) -> Boolean,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    // 侧边栏展开状态 - 默认收起
    var isNavigationExpanded by remember { mutableStateOf(false) }

    // Material3 风格的背景
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧导航栏 - 始终显示
            DesktopNavigationRail(
                currentDestination = appState.currentDestination,
                onNavigate = { appState.navigateTo(it) },
                isExpanded = isNavigationExpanded,
                onToggleExpand = { isNavigationExpanded = !isNavigationExpanded }
            )

            // 主内容区域 - 包含内容和底部播放栏
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
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
                            // 桌面端使用横向布局的播放详情页
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
                                playbackSpeed = playbackState.playbackSpeed,
                                onSpeedChange = { showSpeedDialog.value = true },
                                sleepTimerMinutes = if (sleepTimerState.isActive) sleepTimerState.remainingMinutes else null,
                                onSleepTimerClick = { showSleepTimerDialog.value = true },
                            )
                        }
                        showRecommendedPodcastDetail.value && selectedRecommendedPodcast.value != null -> {
                            // 显示推荐播客详情页
                            val podcastToShow = selectedRecommendedPodcast.value!!
                            val podcastName = podcastToShow.name
                            com.opoojkk.podium.ui.podcast.RecommendedPodcastDetailScreen(
                                podcast = podcastToShow,
                                onBack = {
                                    showRecommendedPodcastDetail.value = false
                                    selectedRecommendedPodcast.value = null
                                },
                                onSubscribe = { rssUrl ->
                                    controller.subscribe(rssUrl)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已订阅《${podcastName}》")
                                    }
                                },
                                onUnsubscribe = { rssUrl ->
                                    controller.unsubscribeByFeedUrl(rssUrl)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已取消订阅《${podcastName}》")
                                    }
                                },
                                checkIfSubscribed = { rssUrl ->
                                    controller.checkIfSubscribed(rssUrl)
                                },
                                onPlayEpisode = { rssEpisode ->
                                    // Convert RssEpisode to Episode for playback
                                    val episode = Episode(
                                        id = rssEpisode.id,
                                        podcastId = podcastToShow.id,
                                        podcastTitle = podcastToShow.name,
                                        title = rssEpisode.title,
                                        description = rssEpisode.description,
                                        audioUrl = rssEpisode.audioUrl,
                                        publishDate = rssEpisode.publishDate,
                                        duration = rssEpisode.duration,
                                        imageUrl = rssEpisode.imageUrl,
                                        chapters = rssEpisode.chapters,
                                    )
                                    onPlayEpisode(episode)
                                },
                                loadPodcastFeed = { feedUrl ->
                                    kotlin.runCatching {
                                        com.opoojkk.podium.data.rss.PodcastFeedService(
                                            httpClient = environment.httpClient,
                                            parser = com.opoojkk.podium.data.rss.createDefaultRssParser()
                                        ).fetch(feedUrl)
                                    }
                                },
                                currentPlayingEpisodeId = playbackState.episode?.id,
                                isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                onPauseResume = {
                                    if (playbackState.isPlaying) {
                                        controller.pause()
                                    } else {
                                        controller.resume()
                                    }
                                },
                            )
                        }
                        selectedCategory.value != null -> {
                            // 显示分类详情页
                            val category = selectedCategory.value!!
                            CategoryDetailScreen(
                                category = category,
                                onBack = { selectedCategory.value = null },
                                onPodcastClick = { podcast ->
                                    selectedRecommendedPodcast.value = podcast
                                    showRecommendedPodcastDetail.value = true
                                },
                                loadPodcastArtwork = { podcasts ->
                                    recommendedPodcastRepository.loadPodcastsWithArtwork(podcasts)
                                }
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
                                currentPlayingEpisodeId = playbackState.episode?.id,
                                isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                onPauseResume = {
                                    if (playbackState.isPlaying) {
                                        controller.pause()
                                    } else {
                                        controller.resume()
                                    }
                                },
                                onAddToPlaylist = { episodeId ->
                                    controller.addToPlaylist(episodeId)
                                },
                                onUnsubscribe = {
                                    controller.deleteSubscription(podcast.id)
                                    selectedPodcast.value = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已取消订阅《${podcast.title}》")
                                    }
                                },
                                onEpisodeClick = { episode ->
                                    // 播放单集并打开播放器详情页
                                    onPlayEpisode(episode)
                                    showPlayerDetail.value = true
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
                                currentPlayingEpisodeId = playbackState.episode?.id,
                                isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                onPauseResume = {
                                    if (playbackState.isPlaying) {
                                        controller.pause()
                                    } else {
                                        controller.resume()
                                    }
                                },
                                onAddToPlaylist = { episodeId ->
                                    controller.addToPlaylist(episodeId)
                                },
                            )
                        }
                        else -> {
                            when (appState.currentDestination) {
                                PodiumDestination.Home -> HomeScreen(
                                    state = homeState.copy(
                                        hotEpisodes = hotEpisodes,
                                        hotPodcasts = hotPodcasts,
                                        newEpisodes = newEpisodes,
                                        newPodcasts = newPodcasts
                                    ),
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
                                    onPodcastClick = onPodcastClick,
                                    currentPlayingEpisodeId = playbackState.episode?.id,
                                    isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                    onPauseResume = {
                                        if (playbackState.isPlaying) {
                                            controller.pause()
                                        } else {
                                            controller.resume()
                                        }
                                    },
                                    onAddToPlaylist = { episodeId ->
                                        controller.addToPlaylist(episodeId)
                                    },
                                    onEpisodeClick = { episode ->
                                        // 播放单集并打开播放器详情页
                                        onPlayEpisode(episode)
                                        showPlayerDetail.value = true
                                    },
                                    onLoadMoreSearchResults = controller::loadMoreSearchResults,
                                    onSearchFilterTypeChange = controller::setSearchFilterType,
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

                                PodiumDestination.Categories -> CategoriesScreen(
                                    categories = categories,
                                    isLoading = categoriesLoading,
                                    onCategoryClick = { category -> selectedCategory.value = category }
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

                // 底部播放控制器 - 播放详情展开时隐藏，简化动画
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showPlayerDetail.value && selectedCategory.value == null && !showRecommendedPodcastDetail.value,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
                    )
                ) {
                    PlaybackBar(
                        playbackState = playbackState,
                        onPlayPauseClick = {
                            if (playbackState.isPlaying) {
                                controller.pause()
                            } else {
                                controller.resume()
                            }
                        },
                        onBarClick = { showPlayerDetail.value = true }
                    )
                }
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
    selectedCategory: androidx.compose.runtime.MutableState<PodcastCategory?>,
    selectedRecommendedPodcast: androidx.compose.runtime.MutableState<com.opoojkk.podium.data.model.recommended.RecommendedPodcast?>,
    showRecommendedPodcastDetail: androidx.compose.runtime.MutableState<Boolean>,
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
    categories: List<PodcastCategory>,
    categoriesLoading: Boolean,
    recommendedPodcastRepository: RecommendedPodcastRepository,
    hotEpisodes: List<EpisodeWithPodcast>,
    hotPodcasts: List<Podcast>,
    newEpisodes: List<EpisodeWithPodcast>,
    newPodcasts: List<Podcast>,
    environment: PodiumEnvironment,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
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
                    visible = !showPlayerDetail.value && !showPlaylist.value && showViewMore.value == null && !showCacheManagement.value && selectedCategory.value == null && !showRecommendedPodcastDetail.value,
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
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                    showRecommendedPodcastDetail.value && selectedRecommendedPodcast.value != null -> {
                        // 显示推荐播客详情页
                        val podcastToShow = selectedRecommendedPodcast.value!!
                        val podcastName = podcastToShow.name
                        com.opoojkk.podium.ui.podcast.RecommendedPodcastDetailScreen(
                            podcast = podcastToShow,
                            onBack = {
                                showRecommendedPodcastDetail.value = false
                                selectedRecommendedPodcast.value = null
                            },
                            onSubscribe = { rssUrl ->
                                controller.subscribe(rssUrl)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已订阅《${podcastName}》")
                                }
                            },
                            onUnsubscribe = { rssUrl ->
                                controller.unsubscribeByFeedUrl(rssUrl)
                                scope.launch {
                                    snackbarHostState.showSnackbar("已取消订阅《${podcastName}》")
                                }
                            },
                            checkIfSubscribed = { rssUrl ->
                                controller.checkIfSubscribed(rssUrl)
                            },
                            onPlayEpisode = { rssEpisode ->
                                // Convert RssEpisode to Episode for playback
                                val episode = Episode(
                                    id = rssEpisode.id,
                                    podcastId = podcastToShow.id,
                                    podcastTitle = podcastToShow.name,
                                    title = rssEpisode.title,
                                    description = rssEpisode.description,
                                    audioUrl = rssEpisode.audioUrl,
                                    publishDate = rssEpisode.publishDate,
                                    duration = rssEpisode.duration,
                                    imageUrl = rssEpisode.imageUrl,
                                    chapters = rssEpisode.chapters,
                                )
                                onPlayEpisode(episode)
                            },
                            loadPodcastFeed = { feedUrl ->
                                kotlin.runCatching {
                                    com.opoojkk.podium.data.rss.PodcastFeedService(
                                        httpClient = environment.httpClient,
                                        parser = com.opoojkk.podium.data.rss.createDefaultRssParser()
                                    ).fetch(feedUrl)
                                }
                            },
                            currentPlayingEpisodeId = playbackState.episode?.id,
                            isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                            onPauseResume = {
                                if (playbackState.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.resume()
                                }
                            },
                        )
                    }
                    selectedCategory.value != null -> {
                        // 显示分类详情页
                        val category = selectedCategory.value!!
                        CategoryDetailScreen(
                            category = category,
                            onBack = { selectedCategory.value = null },
                            onPodcastClick = { podcast ->
                                selectedRecommendedPodcast.value = podcast
                                showRecommendedPodcastDetail.value = true
                            },
                            loadPodcastArtwork = { podcasts ->
                                recommendedPodcastRepository.loadPodcastsWithArtwork(podcasts)
                            }
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
                            currentPlayingEpisodeId = playbackState.episode?.id,
                            isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                            onPauseResume = {
                                if (playbackState.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.resume()
                                }
                            },
                            onAddToPlaylist = { episodeId ->
                                controller.addToPlaylist(episodeId)
                            },
                            onUnsubscribe = {
                                controller.deleteSubscription(podcast.id)
                                selectedPodcast.value = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("已取消订阅《${podcast.title}》")
                                }
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
                            currentPlayingEpisodeId = playbackState.episode?.id,
                            isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                            onPauseResume = {
                                if (playbackState.isPlaying) {
                                    controller.pause()
                                } else {
                                    controller.resume()
                                }
                            },
                            onAddToPlaylist = { episodeId ->
                                controller.addToPlaylist(episodeId)
                            },
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
                                    state = homeState.copy(
                                        hotEpisodes = hotEpisodes,
                                        hotPodcasts = hotPodcasts,
                                        newEpisodes = newEpisodes,
                                        newPodcasts = newPodcasts
                                    ),
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
                                    onPodcastClick = onPodcastClick,
                                    currentPlayingEpisodeId = playbackState.episode?.id,
                                    isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                    onPauseResume = {
                                        if (playbackState.isPlaying) {
                                            controller.pause()
                                        } else {
                                            controller.resume()
                                        }
                                    },
                                    onAddToPlaylist = { episodeId ->
                                        controller.addToPlaylist(episodeId)
                                    },
                                    onEpisodeClick = { episode ->
                                        // 播放单集并打开播放器详情页
                                        onPlayEpisode(episode)
                                        showPlayerDetail.value = true
                                    },
                                    onLoadMoreSearchResults = controller::loadMoreSearchResults,
                                    onSearchFilterTypeChange = controller::setSearchFilterType,
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

                                PodiumDestination.Categories -> CategoriesScreen(
                                    categories = categories,
                                    isLoading = categoriesLoading,
                                    onCategoryClick = { category -> selectedCategory.value = category }
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
