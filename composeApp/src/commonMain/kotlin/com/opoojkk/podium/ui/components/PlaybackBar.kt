package com.opoojkk.podium.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.PlaybackState

@Composable
fun PlaybackBar(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Debug: Always show the playback bar to see the state
    println("PlaybackBar: episode=${playbackState.episode?.title}, isPlaying=${playbackState.isPlaying}, position=${playbackState.positionMs}")

    if (playbackState.episode == null) {
        return
    }
    // Always show the playback bar, even when no episode is playing
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onBarClick() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        // 向上滑动（负值）超过阈值时触发
                        if (dragAmount < -10) {
                            onBarClick()
                        }
                    }
                )
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        val durationMs = playbackState.episode.duration ?: playbackState.durationMs
        val timeText = buildString {
            append(formatTime(playbackState.positionMs))
            append(" / ")
            append(durationMs?.let { formatTime(it) } ?: "--:--")
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play/Pause or Buffering indicator
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    FilledTonalIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Episode info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = playbackState.episode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playbackState.episode.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time info: 当前/总时长
                val rightText = if (playbackState.isBuffering) "加载中…" else timeText
                Text(
                    text = rightText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress bar at bottom
            val progress = if (durationMs != null && durationMs > 0) {
                (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f // 没有时长信息时显示0进度，而不是加载动画
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
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
