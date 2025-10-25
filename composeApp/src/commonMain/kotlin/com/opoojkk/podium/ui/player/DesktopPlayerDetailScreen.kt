package com.opoojkk.podium.ui.player

import androidx.compose.foundation.background
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
) {
    val episode = playbackState.episode ?: return
    val durationMs = episode.duration ?: playbackState.durationMs
    
    // 记住滚动状态，避免重组时重新创建
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
                    .height(64.dp), // 固定高度
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )

                    // 操作按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { /* TODO: 收藏 */ }) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "收藏",
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

            // 主内容区域 - 横向布局，使用固定padding确保布局稳定
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 使用weight而不是fillMaxSize，更稳定
                    .padding(horizontal = 64.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 左侧：专辑封面和基本信息
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // 专辑封面
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(120.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 剧集标题 - 固定高度避免跳动
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp) // 固定2行文字的高度
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 播客名称 - 固定高度避免跳动
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp) // 固定1行文字的高度
                    )
                }

                // 右侧：播放控制和信息
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(24.dp)) // 与左侧对称的顶部间距
                    
                    // 剧集描述（可选）- 使用固定高度避免布局跳动
                    if (episode.description.isNotBlank()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 400.dp), // 设置最大高度
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(scrollState) // 使用外部的scrollState
                            ) {
                                Text(
                                    text = "剧集简介",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Text(
                                    text = episode.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))

                    // 播放控制区域
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 进度条 - 固定高度避免跳动
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp), // 稍微减小高度
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (durationMs != null && durationMs > 0) {
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
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            // 时间显示 - 固定宽度避免跳动
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp), // 固定高度
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(
                                        if (isDragging) sliderPosition.toLong() else playbackState.positionMs
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(60.dp) // 固定宽度
                                )
                                Text(
                                    text = durationMs?.let { formatTime(it) } ?: "--:--",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(60.dp) // 固定宽度
                                )
                            }
                        }

                        // 播放控制按钮 - 固定高度避免跳动
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp), // 稍微减小高度
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 后退按钮
                            IconButton(
                                onClick = onSeekBack,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "后退15秒",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // 播放/暂停按钮 - Material3 风格
                            FilledIconButton(
                                onClick = onPlayPause,
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) 
                                        Icons.Default.Pause 
                                    else 
                                        Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // 前进按钮
                            IconButton(
                                onClick = onSeekForward,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "前进30秒",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // 额外控制（音量、播放速度等）- 固定高度避免跳动
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp), // 减小高度
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 播放速度
                            TextButton(onClick = { /* TODO: 播放速度 */ }) {
                                Text(
                                    text = "1.0x",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // 设置
                            IconButton(onClick = { /* TODO: 设置 */ }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
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

