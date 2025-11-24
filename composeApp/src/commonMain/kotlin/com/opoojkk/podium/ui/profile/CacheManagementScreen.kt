package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.presentation.ProfileCachedItem
import com.opoojkk.podium.presentation.ProfileDownloadItem
import com.opoojkk.podium.presentation.ProfileUiState
import com.opoojkk.podium.platform.BackHandler
import kotlin.math.roundToInt

private sealed interface CacheManagementViewState {
    data object Overview : CacheManagementViewState
    data object CachedDetail : CacheManagementViewState
    data object DownloadDetail : CacheManagementViewState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    state: ProfileUiState,
    onBackClick: () -> Unit,
    onTogglePodcastAutoDownload: (String, Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewState by remember { mutableStateOf<CacheManagementViewState>(CacheManagementViewState.Overview) }
    val cached = state.cachedDownloads
    val inProgress = state.inProgressDownloads
    val queued = state.queuedDownloads

    val title = when (viewState) {
        CacheManagementViewState.Overview -> "缓存管理"
        CacheManagementViewState.CachedDetail -> "已缓存"
        CacheManagementViewState.DownloadDetail -> "下载详情"
    }

    // 处理系统返回按钮
    BackHandler {
        viewState = when (viewState) {
            CacheManagementViewState.Overview -> {
                onBackClick()
                CacheManagementViewState.Overview
            }
            CacheManagementViewState.CachedDetail,
            CacheManagementViewState.DownloadDetail -> CacheManagementViewState.Overview
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewState = when (viewState) {
                                CacheManagementViewState.Overview -> {
                                    onBackClick()
                                    CacheManagementViewState.Overview
                                }

                                CacheManagementViewState.CachedDetail,
                                CacheManagementViewState.DownloadDetail -> CacheManagementViewState.Overview
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        when (viewState) {
            CacheManagementViewState.Overview -> CacheOverviewContent(
                cacheSizeInMb = state.cacheSizeInMb,
                cached = cached,
                inProgress = inProgress,
                queued = queued,
                onShowCached = { viewState = CacheManagementViewState.CachedDetail },
                onShowDownloads = { viewState = CacheManagementViewState.DownloadDetail },
                subscribedPodcasts = state.subscribedPodcasts,
                onTogglePodcastAutoDownload = onTogglePodcastAutoDownload,
                onClearCache = onClearCache,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            CacheManagementViewState.CachedDetail -> CachedDetailContent(
                cached = cached,
                inProgress = inProgress,
                queued = queued,
                onShowDownloads = { viewState = CacheManagementViewState.DownloadDetail },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )

            CacheManagementViewState.DownloadDetail -> DownloadDetailContent(
                inProgress = inProgress,
                queued = queued,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }
}

@Composable
private fun CacheOverviewContent(
    cacheSizeInMb: Int,
    cached: List<ProfileCachedItem>,
    inProgress: List<ProfileDownloadItem>,
    queued: List<ProfileDownloadItem>,
    onShowCached: () -> Unit,
    onShowDownloads: () -> Unit,
    subscribedPodcasts: List<Podcast>,
    onTogglePodcastAutoDownload: (String, Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CachedSummaryCard(
                cachedCount = cached.size,
                onClick = onShowCached,
            )
        }
        item {
            DownloadSummaryCard(
                inProgressCount = inProgress.size,
                queuedCount = queued.size,
                onClick = onShowDownloads,
            )
        }

        item {
            Text(
                text = "自动缓存设置",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }

        item {
            Text(
                text = "为每个播客单独设置自动缓存。开启后将下载该播客的所有节目，并在检测到更新时自动下载新节目。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (subscribedPodcasts.isEmpty()) {
            item {
                Text(
                    text = "暂无订阅的播客",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(
                items = subscribedPodcasts,
                key = { it.id }
            ) { podcast ->
                PodcastAutoDownloadCard(
                    podcast = podcast,
                    onToggleAutoDownload = { enabled -> onTogglePodcastAutoDownload(podcast.id, enabled) },
                )
            }
        }
    }
}

@Composable
private fun CachedDetailContent(
    cached: List<ProfileCachedItem>,
    inProgress: List<ProfileDownloadItem>,
    queued: List<ProfileDownloadItem>,
    onShowDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cached.isEmpty()) {
            item {
                Text(
                    text = "暂无已缓存的节目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(
                items = cached,
                key = { it.episodeId }
            ) { item ->
                CachedItemCard(item)
            }
        }

        item {
            DownloadSummaryCard(
                inProgressCount = inProgress.size,
                queuedCount = queued.size,
                onClick = onShowDownloads,
            )
        }
    }
}

@Composable
private fun DownloadDetailContent(
    inProgress: List<ProfileDownloadItem>,
    queued: List<ProfileDownloadItem>,
    modifier: Modifier = Modifier,
) {
    if (inProgress.isEmpty() && queued.isEmpty()) {
        Column(
            modifier = modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂无正在下载或排队的节目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inProgress.isNotEmpty()) {
                item {
                    Text(
                        text = "正在缓存",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(
                    items = inProgress,
                    key = { it.episodeId }
                ) { item ->
                    DownloadItemCard(item)
                }
            }

            if (queued.isNotEmpty()) {
                item {
                    Text(
                        text = "等待缓存",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (inProgress.isNotEmpty()) 8.dp else 0.dp),
                    )
                }
                items(
                    items = queued,
                    key = { it.episodeId }
                ) { item ->
                    DownloadItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun CacheInfoCard(cacheSizeInMb: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("缓存信息", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("已缓存内容") },
                supportingContent = { Text("占用存储空间") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                trailingContent = {
                    Text(
                        "$cacheSizeInMb MB",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

@Composable
private fun CachedSummaryCard(
    cachedCount: Int,
    onClick: () -> Unit,
) {
    val supportingText = if (cachedCount > 0) {
        "已缓存 $cachedCount 项"
    } else {
        "暂无已缓存的节目"
    }

    Card(
        onClick = onClick,
        enabled = cachedCount > 0,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            headlineContent = { Text("已缓存") },
            supportingContent = { Text(supportingText) },
            leadingContent = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun DownloadSummaryCard(
    inProgressCount: Int,
    queuedCount: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val supportingText = when {
        inProgressCount > 0 && queuedCount > 0 -> "正在缓存 $inProgressCount 项 · 排队 $queuedCount 项"
        inProgressCount > 0 -> "正在缓存 $inProgressCount 项"
        queuedCount > 0 -> "排队 $queuedCount 项"
        else -> "暂无正在下载或排队的节目"
    }

    Card(
        onClick = onClick,
        enabled = enabled && (inProgressCount > 0 || queuedCount > 0),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            headlineContent = { Text("缓存中的节目") },
            supportingContent = { Text(supportingText) },
            leadingContent = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun CachedItemCard(item: ProfileCachedItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧图标
            PodcastArtwork(
                artworkUrl = item.podcastArtworkUrl,
                podcastTitle = item.podcastTitle,
                modifier = Modifier.size(56.dp),
            )

            // 右侧内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = item.podcastTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val sizeMb = item.sizeBytes.coerceAtLeast(0L) / (1024f * 1024f)
                Text(
                    text = "${(sizeMb * 10).toInt() / 10.0} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun DownloadItemCard(download: ProfileDownloadItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧图标
            PodcastArtwork(
                artworkUrl = download.podcastArtworkUrl,
                podcastTitle = download.podcastTitle,
                modifier = Modifier.size(56.dp),
            )

            // 右侧内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = download.episodeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = download.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (val status = download.status) {
                    is DownloadStatus.InProgress -> {
                        val progress = status.progress.coerceIn(0f, 1f)
                        val progressPercent = (progress * 100).roundToInt().coerceIn(0, 100)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is DownloadStatus.Idle -> {
                        Text(
                            text = "等待下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is DownloadStatus.Failed -> {
                        Text(
                            text = "下载失败：${status.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is DownloadStatus.Completed -> {
                        Text(
                            text = "下载已完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheActionsCard(onClearCache: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("管理操作", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("清除所有缓存") },
                supportingContent = { Text("删除所有已下载的节目文件") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = onClearCache) {
                        Text("清除")
                    }
                },
            )
        }
    }
}

@Composable
private fun PodcastAutoDownloadCard(
    podcast: Podcast,
    onToggleAutoDownload: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧图标 - 显示播客logo
            PodcastArtwork(
                artworkUrl = podcast.artworkUrl,
                podcastTitle = podcast.title,
                modifier = Modifier.size(56.dp),
            )

            // 中间内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = if (podcast.autoDownload) "已启用自动缓存所有节目"
                    else "未启用自动缓存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 右侧开关
            Switch(
                checked = podcast.autoDownload,
                onCheckedChange = onToggleAutoDownload,
            )
        }
    }
}

@Composable
private fun PodcastArtwork(
    artworkUrl: String?,
    podcastTitle: String,
    modifier: Modifier = Modifier
) {
    val initials = podcastTitle.trim().split(" ", limit = 2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString(separator = "")
        .takeIf { it.isNotBlank() }
        ?: "P"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = podcastTitle,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                error = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
