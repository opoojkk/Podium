package com.opoojkk.podium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.PlaybackState

/**
 * Spotify风格的桌面播放控制器
 * 固定在底部，横跨整个宽度
 */
@Composable
fun DesktopPlaybackBar(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playbackState.episode == null) {
        return
    }

    val durationMs = playbackState.episode.duration ?: playbackState.durationMs
    var sliderPosition by remember { mutableStateOf(playbackState.positionMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }

    // 更新 slider 位置（仅在不拖动且未等待 seek 时）
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

    // Material3 风格的背景 - 使用更清晰的视觉层次
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 进度条 - 放在最顶部，Spotify风格
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // 没有时长信息时显示固定的进度条（0进度）
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // 主要控制区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧：歌曲信息
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onBarClick() },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 专辑封面占位符（如果有封面图可以在这里显示）
                    Surface(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // 歌曲标题和播客名称
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = playbackState.episode.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = playbackState.episode.podcastTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 收藏按钮（可选）
                    IconButton(
                        onClick = { /* TODO: 实现收藏功能 */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 中间：播放控制
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 控制按钮行
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 后退15秒
                        IconButton(
                            onClick = onSeekBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "后退15秒",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 播放/暂停按钮 - Material3 风格
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(52.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            FilledIconButton(
                                onClick = onPlayPauseClick,
                                modifier = Modifier.size(52.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying)
                                        Icons.Default.Pause
                                    else
                                        Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // 前进30秒
                        IconButton(
                            onClick = onSeekForward,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "前进30秒",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // 时间显示
                    Row(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(sliderPosition.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationMs?.let { formatTime(it) } ?: "--:--",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 右侧：音量和其他控制
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 更多选项按钮
                    IconButton(
                        onClick = { /* TODO: 打开更多选项 */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 详情按钮
                    IconButton(
                        onClick = onBarClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
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

