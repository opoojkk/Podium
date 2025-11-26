package com.opoojkk.podium.ui.playlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaylistItem
import com.opoojkk.podium.presentation.PlaylistUiState
import com.opoojkk.podium.platform.BackHandler
import com.opoojkk.podium.ui.components.ArtworkWithPlaceholder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    state: PlaylistUiState,
    onPlayEpisode: (Episode) -> Unit,
    onMarkCompleted: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onPlayNext: (Episode) -> Unit = {},
    onDownload: (Episode) -> Unit = {},
    onShare: (Episode) -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // 处理系统返回按钮
    onBack?.let { BackHandler(onBack = it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放列表") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "播放列表为空",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "开始播放节目后会自动添加到此处",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    state.items,
                    key = { it.episode.id }
                ) { item ->
                    PlaylistItemCard(
                        item = item,
                        onPlayClick = { onPlayEpisode(item.episode) },
                        onMarkCompleted = { onMarkCompleted(item.episode.id) },
                        onRemoveFromPlaylist = { onRemoveFromPlaylist(item.episode.id) },
                        onPlayNext = { onPlayNext(item.episode) },
                        onDownload = { onDownload(item.episode) },
                        onShare = { onShare(item.episode) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(durationMillis = 300),
                            fadeOutSpec = tween(durationMillis = 300),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistItemCard(
    item: PlaylistItem,
    onPlayClick: () -> Unit,
    onMarkCompleted: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = item.progress.durationMs?.let { duration ->
        if (duration > 0) {
            (item.progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else 0f
    } ?: 0f

    var showBottomSheet by remember { mutableStateOf(false) }
    val isPlaying = false // TODO: 需要从外部传入当前播放状态

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlayClick)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 播客封面
                ArtworkWithPlaceholder(
                    artworkUrl = item.podcast.artworkUrl,
                    title = item.podcast.title,
                    size = 56.dp,
                    cornerRadius = 8.dp,
                    contentDescription = item.podcast.title
                )

                // 中间内容区域
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.episode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 右侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 播放/暂停按钮
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 更多按钮
                    IconButton(
                        onClick = { showBottomSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 底部进度条
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            )
        }
    }

    // 底部抽屉
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // 标题区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = item.episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 菜单选项
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.PlaylistPlay, contentDescription = null) },
                    label = { Text("下一集播放") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onPlayNext()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                    label = { Text("标记完成") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onMarkCompleted()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    label = { Text("下载") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onDownload()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    label = { Text("查看详情") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        // TODO: 实现查看详情
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    label = { Text("分享") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onShare()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    label = { Text("移除") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onRemoveFromPlaylist()
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = MaterialTheme.colorScheme.error,
                        unselectedTextColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}


private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}
