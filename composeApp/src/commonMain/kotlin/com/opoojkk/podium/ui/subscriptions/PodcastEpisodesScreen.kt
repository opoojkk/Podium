package com.opoojkk.podium.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.ui.components.PodcastEpisodeCard
import com.opoojkk.podium.platform.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SortOrder {
    DESCENDING, // 降序（新到旧）
    ASCENDING   // 升序（旧到新）
}

/**
 * 播客单集列表页面，显示某个播客的所有剧集
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastEpisodesScreen(
    podcast: Podcast,
    episodes: List<EpisodeWithPodcast>,
    onPlayEpisode: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    downloads: Map<String, DownloadStatus> = emptyMap(),
    onDownloadEpisode: (Episode) -> Unit = {},
    onRefresh: ((Int) -> Unit) -> Unit = {},
    currentPlayingEpisodeId: String? = null,
) {
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 处理系统返回按钮
    BackHandler(onBack = onBack)

    // 根据排序顺序对剧集进行排序
    val sortedEpisodes = remember(episodes, sortOrder) {
        when (sortOrder) {
            SortOrder.DESCENDING -> episodes.sortedByDescending { it.episode.publishDate }
            SortOrder.ASCENDING -> episodes.sortedBy { it.episode.publishDate }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = snackbarData,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            )
        },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${episodes.size} 集",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            onRefresh { count ->
                                scope.launch {
                                    isRefreshing = false
                                    val message = if (count > 0) {
                                        "更新完成，发现 $count 个新节目"
                                    } else {
                                        "已是最新"
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                        )
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "排序",
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("时间降序（新→旧）")
                                    if (sortOrder == SortOrder.DESCENDING) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                sortOrder = SortOrder.DESCENDING
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("时间升序（旧→新）")
                                    if (sortOrder == SortOrder.ASCENDING) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                sortOrder = SortOrder.ASCENDING
                                showSortMenu = false
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sortedEpisodes.isEmpty()) {
                item {
                    Text(
                        text = "暂无剧集",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(
                    items = sortedEpisodes,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    PodcastEpisodeCard(
                        episodeWithPodcast = episodeWithPodcast,
                        onPlayClick = { onPlayEpisode(episodeWithPodcast.episode) },
                        downloadStatus = downloads[episodeWithPodcast.episode.id],
                        onDownloadClick = { onDownloadEpisode(episodeWithPodcast.episode) },
                        showDownloadButton = true,
                        isCurrentlyPlaying = episodeWithPodcast.episode.id == currentPlayingEpisodeId,
                    )
                }
            }
        }
    }
}

