package com.opoojkk.podium.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.PlaybackState

/**
 * 桌面版播放详情页
 * 采用横向布局，更符合桌面操作习惯
 */
@Composable
fun DesktopPlayerDetailScreen(
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaylistClick: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onSpeedChange: () -> Unit = { /* TODO: 打开倍速选择对话框 */ },
    sleepTimerMinutes: Int? = null,
    onSleepTimerClick: () -> Unit = { /* TODO: 打开睡眠定时对话框 */ },
) {
    val episode = playbackState.episode ?: return
    val durationMs = episode.duration ?: playbackState.durationMs
    
    // 记住滚动状态，避免重组时重新创建
    val scrollState = rememberScrollState()
    
    // Slider状态管理
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(playbackState.positionMs.toFloat()) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }

    // 更新slider位置（只在不拖动且未等待 seek 时）
    LaunchedEffect(playbackState.positionMs) {
        if (!isDragging) {
            // 如果正在等待 seek 完成，检查是否已经接近目标位置
            if (seekTarget != null) {
                val diff = kotlin.math.abs(playbackState.positionMs - seekTarget!!)
                if (diff < 2000) { // 如果在 2 秒内，认为 seek 完成
                    seekTarget = null
                }
            }

            // 如果没有等待 seek，正常更新位置
            if (seekTarget == null) {
                sliderPosition = playbackState.positionMs.toFloat()
            }
        }
    }

    // Material3 背景
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部工具栏 - 固定高度避免跳动
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp), // 固定高度
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 返回按钮
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 播客标题
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )

                    // 操作按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onPlaylistClick) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = "播放列表",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { /* TODO: 更多 */ }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 主内容区域 - 横向布局：左侧封面+进度+控制，右侧名称+描述
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：专辑封面、进度条和播放控制
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 专辑封面
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
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
                                coil3.compose.AsyncImage(
                                    model = episode.imageUrl,
                                    contentDescription = episode.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Podcasts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(100.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 进度条区域
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (durationMs != null && durationMs > 0) {
                            Slider(
                                value = sliderPosition,
                                onValueChange = { newValue ->
                                    isDragging = true
                                    sliderPosition = newValue
                                },
                                onValueChangeFinished = {
                                    val targetPosition = sliderPosition.toLong()
                                    seekTarget = targetPosition
                                    onSeekTo(targetPosition)
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
                                text = formatTime(sliderPosition.toLong()),
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

                    // 播放控制按钮
                    PlaybackDetailControls(
                        isPlaying = playbackState.isPlaying,
                        onPlayPause = onPlayPause,
                        onSeekBack = onSeekBack,
                        onSeekForward = onSeekForward,
                        modifier = Modifier.fillMaxWidth(),
                        isBuffering = playbackState.isBuffering,
                        sizing = PlaybackControlDefaults.Default,
                        playbackSpeed = playbackSpeed,
                        onSpeedChange = onSpeedChange,
                        sleepTimerMinutes = sleepTimerMinutes,
                        onSleepTimerClick = onSleepTimerClick,
                    )
                }

                // 右侧：剧集名称和描述
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .then(
                            if (episode.description.isNotBlank() || episode.chapters.isNotEmpty()) {
                                Modifier.verticalScroll(scrollState)
                            } else {
                                Modifier
                            }
                        ),
                    verticalArrangement = if (episode.description.isNotBlank() || episode.chapters.isNotEmpty()) {
                        Arrangement.Top
                    } else {
                        Arrangement.Center
                    }
                ) {
                    // 剧集标题
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 播客名称
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 剧集描述
                    if (episode.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "剧集简介",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        com.opoojkk.podium.ui.components.HtmlText(
                            html = episode.description,
                            onTimestampClick = { timestampMs ->
                                onSeekTo(timestampMs)
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6
                            )
                        )
                    }

                    // 章节列表（如果有）
                    if (episode.chapters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "章节",
                            style = MaterialTheme.typography.titleLarge,
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
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 章节时间
        Text(
            text = formatTime(chapter.startTimeMs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 60.dp)
        )

        // 章节标题
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // 播放图标
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放章节",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
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

