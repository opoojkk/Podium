package com.opoojkk.podium.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.ui.components.PodcastEpisodeCard
import com.opoojkk.podium.platform.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    onPauseResume: () -> Unit = {},
    onAddToPlaylist: (String) -> Unit = {},
    onUnsubscribe: () -> Unit = {},
) {
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentPage by remember { mutableStateOf(1) }
    val itemsPerPage = 20

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
                title = { Text("播客详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
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
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 播客信息头部
                item {
                    SubscribedPodcastHeader(
                        podcast = podcast,
                        episodeCount = episodes.size,
                        onUnsubscribeClick = onUnsubscribe
                    )
                }

            // 单集列表标题和排序按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单集列表",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    // 排序按钮
                    FilterChip(
                        selected = false,
                        onClick = {
                            sortOrder = when (sortOrder) {
                                SortOrder.DESCENDING -> SortOrder.ASCENDING
                                SortOrder.ASCENDING -> SortOrder.DESCENDING
                            }
                        },
                        label = {
                            Text(
                                text = when (sortOrder) {
                                    SortOrder.DESCENDING -> "时间从晚到早"
                                    SortOrder.ASCENDING -> "时间从早到晚"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "排序",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

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
                val totalItems = sortedEpisodes.size
                val displayedEpisodes = sortedEpisodes.take(currentPage * itemsPerPage)
                val hasMore = displayedEpisodes.size < totalItems

                items(
                    items = displayedEpisodes,
                    key = { it.episode.id }
                ) { episodeWithPodcast ->
                    val isCurrentEpisode = episodeWithPodcast.episode.id == currentPlayingEpisodeId
                    PodcastEpisodeCard(
                        episodeWithPodcast = episodeWithPodcast,
                        onPlayClick = {
                            if (isCurrentEpisode) {
                                // 如果是当前播放的单集，切换播放/暂停
                                onPauseResume()
                            } else {
                                // 如果是其他单集，播放它
                                onPlayEpisode(episodeWithPodcast.episode)
                            }
                        },
                        downloadStatus = downloads[episodeWithPodcast.episode.id],
                        onDownloadClick = { onDownloadEpisode(episodeWithPodcast.episode) },
                        onAddToPlaylist = { onAddToPlaylist(episodeWithPodcast.episode.id) },
                        showDownloadButton = true,
                        isCurrentlyPlaying = isCurrentEpisode && isPlaying,
                        isBuffering = isCurrentEpisode && isBuffering,
                        showPlaybackStatus = false,
                    )
                }

                // 加载更多按钮
                if (hasMore) {
                    item {
                        Button(
                            onClick = { currentPage++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text("加载更多 (${displayedEpisodes.size}/$totalItems)")
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SubscribedPodcastHeader(
    podcast: Podcast,
    episodeCount: Int,
    onUnsubscribeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val artworkUrl = podcast.artworkUrl
                val initials = podcast.title
                    .trim()
                    .split(" ", limit = 2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString(separator = "")
                    .takeIf { it.isNotBlank() }
                    ?: "播客"

                if (!artworkUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = artworkUrl,
                        contentDescription = podcast.title,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        error = {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    )
                } else {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 名称和取消订阅按钮
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Button(
                    onClick = onUnsubscribeClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("取消订阅")
                }
            }
        }

        // 描述
        if (!podcast.description.isNullOrBlank()) {
            Text(
                text = podcast.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 上次更新时间和单集数量
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 $episodeCount 集",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "上次更新：${formatLastUpdated(podcast.lastUpdated)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatLastUpdated(instant: kotlinx.datetime.Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}年${localDateTime.monthNumber}月${localDateTime.dayOfMonth}日"
}

@Composable
private fun Spacer(modifier: Modifier) {
    Box(modifier)
}

