package com.opoojkk.podium.ui.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.platform.BackHandler
import com.opoojkk.podium.ui.components.DownloadButton
import com.opoojkk.podium.ui.components.ChapterProgressBar
import com.opoojkk.podium.ui.components.TimestampText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onFavoriteClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    downloadStatus: DownloadStatus? = null,
    onDownloadClick: () -> Unit = {},
    onMarkCompleted: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onShareClick: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onSpeedChange: () -> Unit = { /* TODO: 打开倍速选择对话框 */ },
    sleepTimerMinutes: Int? = null,
    onSleepTimerClick: () -> Unit = { /* TODO: 打开睡眠定时对话框 */ },
) {
    val episode = playbackState.episode ?: return
    val durationMs = episode.duration ?: playbackState.durationMs

    // 底部菜单状态
    var showBottomSheet by remember { mutableStateOf(false) }

    // 处理系统返回按钮
    BackHandler(onBack = onBack)

    // 滚动状态
    val scrollState = rememberScrollState()
    
    // Slider状态管理
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(playbackState.positionMs.toFloat()) }
    
    // 更新slider位置（只在不拖动时）
    LaunchedEffect(playbackState.positionMs) {
        if (!isDragging) {
            sliderPosition = playbackState.positionMs.toFloat()
        }
    }
    
    // 手势支持：当滚动到顶部时，向下滑动关闭
    var dragOffset by remember { mutableStateOf(0f) }
    val canDismiss = scrollState.value == 0 && dragOffset > 100f

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scrollState.value) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (canDismiss) {
                            onBack()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // 只有在滚动到顶部时才允许下拉关闭
                        if (scrollState.value == 0 && dragAmount > 0) {
                            dragOffset += dragAmount
                        }
                    }
                )
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = episode.podcastTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onPlaylistClick) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "播放列表",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 专辑封面
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!episode.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = episode.imageUrl,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 剧集标题
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 播客名称
            Text(
                text = episode.podcastTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 进度条区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (durationMs != null && durationMs > 0) {
                    // 如果有章节信息，使用章节进度条，否则使用普通Slider
                    if (episode.chapters.isNotEmpty()) {
                        ChapterProgressBar(
                            currentPositionMs = if (isDragging) sliderPosition.toLong() else playbackState.positionMs,
                            durationMs = durationMs,
                            chapters = episode.chapters,
                            onSeekTo = { position ->
                                sliderPosition = position.toFloat()
                                onSeekTo(position)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { newValue ->
                                isDragging = true
                                sliderPosition = newValue
                            },
                            onValueChangeFinished = {
                                onSeekTo(sliderPosition.toLong())
                                isDragging = false
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // 时间显示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isDragging) sliderPosition.toLong() else playbackState.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = durationMs?.let { formatTime(it) } ?: "--:--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PlaybackDetailControls(
                isPlaying = playbackState.isPlaying,
                onPlayPause = onPlayPause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                modifier = Modifier.fillMaxWidth(),
                isBuffering = playbackState.isBuffering,
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange,
                sleepTimerMinutes = sleepTimerMinutes,
                onSleepTimerClick = onSleepTimerClick,
            )

            // 章节列表（如果有）
            if (episode.chapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "章节",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        episode.chapters.forEach { chapter ->
                            ChapterItem(
                                chapter = chapter,
                                onClick = { onSeekTo(chapter.startTimeMs) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 剧集描述（如果有）
            if (episode.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "剧集简介",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        com.opoojkk.podium.ui.components.HtmlText(
                            html = episode.description,
                            onTimestampClick = { timestampMs ->
                                onSeekTo(timestampMs)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 更多操作底部抽屉
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
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = episode.podcastTitle,
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
                        // TODO: 实现下一集播放
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
                    icon = {
                        when (downloadStatus) {
                            is DownloadStatus.Completed -> Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            is DownloadStatus.InProgress -> Icon(Icons.Outlined.Download, contentDescription = null)
                            else -> Icon(Icons.Outlined.Download, contentDescription = null)
                        }
                    },
                    label = {
                        Text(when (downloadStatus) {
                            is DownloadStatus.Completed -> "已下载"
                            is DownloadStatus.InProgress -> "下载中..."
                            else -> "下载"
                        })
                    },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onDownloadClick()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    label = { Text("分享") },
                    selected = false,
                    onClick = {
                        showBottomSheet = false
                        onShareClick()
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

@Composable
private fun ChapterItem(
    chapter: com.opoojkk.podium.data.model.Chapter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 章节时间
        Text(
            text = formatTime(chapter.startTimeMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 50.dp)
        )

        // 章节标题
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // 播放图标
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放章节",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (if (milliseconds < 0) 0 else milliseconds) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}
