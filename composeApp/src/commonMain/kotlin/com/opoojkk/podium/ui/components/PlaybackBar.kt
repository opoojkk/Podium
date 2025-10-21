package com.opoojkk.podium.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.PlaybackState

@Composable
fun PlaybackBar(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
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
            .then(if (playbackState.episode != null) Modifier.clickable { onPlayPauseClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (playbackState.episode != null)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        if (playbackState.episode != null) {
            // Show episode info when playing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play/Pause button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Episode info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = playbackState.episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = playbackState.episode.podcastTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Position info
                Text(
                    text = formatTime(playbackState.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
