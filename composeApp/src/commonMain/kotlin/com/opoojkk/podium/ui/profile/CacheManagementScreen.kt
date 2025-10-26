package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
                    .padding(paddingValues)
                    .padding(16.dp),
            )

            CacheManagementViewState.DownloadDetail -> DownloadDetailContent(
                inProgress = inProgress,
                queued = queued,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        item { AutoDownloadDescription() }

        if (subscribedPodcasts.isEmpty()) {
            item { EmptyPodcastCard() }
        } else {
            item {
                PodcastAutoDownloadList(
                    podcasts = subscribedPodcasts,
                    onToggle = onTogglePodcastAutoDownload,
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (cached.isEmpty()) {
            Text(
                text = "暂无已缓存的节目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    cached.forEachIndexed { index, item ->
                        CachedItemRow(item)
                        if (index < cached.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        DownloadSummaryCard(
            inProgressCount = inProgress.size,
            queuedCount = queued.size,
            onClick = onShowDownloads,
        )
    }
}

@Composable
private fun DownloadDetailContent(
    inProgress: List<ProfileDownloadItem>,
    queued: List<ProfileDownloadItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (inProgress.isEmpty() && queued.isEmpty()) {
            Text(
                text = "暂无正在下载或排队的节目",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        DownloadListCard(
            title = "正在缓存",
            downloads = inProgress,
            emptyMessage = "当前没有正在缓存的节目",
        )

        DownloadListCard(
            title = "等待缓存",
            downloads = queued,
            emptyMessage = "当前没有排队中的节目",
        )
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text("已缓存") },
            supportingContent = { Text(supportingText) },
            leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = cachedCount > 0,
                    onClick = onClick,
                ),
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text("缓存中的节目") },
            supportingContent = { Text(supportingText) },
            leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled && (inProgressCount > 0 || queuedCount > 0),
                    onClick = onClick,
                ),
        )
    }
}

@Composable
private fun CachedItemRow(item: ProfileCachedItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.episodeTitle,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = item.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val sizeMb = item.sizeBytes.coerceAtLeast(0L) / (1024f * 1024f)
        Text(
            text = String.format("%.1f MB", sizeMb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadListCard(
    title: String,
    downloads: List<ProfileDownloadItem>,
    emptyMessage: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            ListItem(headlineContent = { Text(title) })
            if (downloads.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                downloads.forEachIndexed { index, item ->
                    DownloadDetailRow(item)
                    if (index < downloads.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadDetailRow(download: ProfileDownloadItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = download.episodeTitle,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = download.podcastTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (val status = download.status) {
            is DownloadStatus.InProgress -> {
                val progress = status.progress.coerceIn(0f, 1f)
                val progressPercent = (progress * 100).roundToInt().coerceIn(0, 100)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            is DownloadStatus.Idle -> {
                Text(
                    text = "等待下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is DownloadStatus.Failed -> {
                Text(
                    text = "下载失败：${status.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is DownloadStatus.Completed -> {
                Text(
                    text = "下载已完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun AutoDownloadDescription() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "自动缓存设置",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "为每个播客单独设置自动缓存。开启后将下载该播客的所有节目，并在检测到更新时自动下载新节目。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyPodcastCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "暂无订阅的播客",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PodcastAutoDownloadList(
    podcasts: List<Podcast>,
    onToggle: (String, Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            podcasts.forEachIndexed { index, podcast ->
                PodcastAutoDownloadItem(
                    podcast = podcast,
                    onToggleAutoDownload = { enabled -> onToggle(podcast.id, enabled) },
                )
                if (index < podcasts.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PodcastAutoDownloadItem(
    podcast: Podcast,
    onToggleAutoDownload: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(podcast.title) },
        supportingContent = {
            Text(
                if (podcast.autoDownload) "已启用自动缓存所有节目"
                else "未启用自动缓存",
            )
        },
        trailingContent = {
            Switch(
                checked = podcast.autoDownload,
                onCheckedChange = onToggleAutoDownload,
            )
        },
    )
}
