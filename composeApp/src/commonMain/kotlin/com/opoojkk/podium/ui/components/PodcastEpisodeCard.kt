package com.opoojkk.podium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun PodcastEpisodeCard(
    episodeWithPodcast: EpisodeWithPodcast,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    downloadStatus: DownloadStatus? = null,
    onDownloadClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    showDownloadButton: Boolean = downloadStatus != null,
    showMoreButton: Boolean = true,
    showDescription: Boolean = true,
    compact: Boolean = false,
    isCurrentlyPlaying: Boolean = false,
    isBuffering: Boolean = false,
    showPlaybackStatus: Boolean = false,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .padding(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 播放状态指示器 - 只在 showPlaybackStatus 为 true 时显示
            if (showPlaybackStatus) {
                Box(
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentlyPlaying) {
                        // 显示播放中的指示器
                        Box(
                            modifier = Modifier
                                .size(if (compact) 24.dp else 28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "正在播放",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                            )
                        }
                    } else {
                        // 显示未播放的指示器（空心圆点）
                        Box(
                            modifier = Modifier
                                .size(if (compact) 12.dp else 14.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                }
            }

            ArtworkPlaceholder(
                artworkUrl = episodeWithPodcast.podcast.artworkUrl,
                title = episodeWithPodcast.podcast.title,
                modifier = Modifier.size(if (compact) 56.dp else 80.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            ) {
                Text(
                    text = episodeWithPodcast.episode.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episodeWithPodcast.podcast.title,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 显示描述
                if (showDescription && !compact && episodeWithPodcast.episode.description.isNotBlank()) {
                    Text(
                        text = episodeWithPodcast.episode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 显示更新时间
                if (!compact) {
                    Text(
                        text = episodeWithPodcast.episode.publishDate
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                            .toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 播放/暂停按钮和更多按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(if (compact) 40.dp else 48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (compact) 22.dp else 24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentlyPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(if (compact) 22.dp else 24.dp)
                        )
                    }
                }

                // 更多按钮
                if (showMoreButton && !compact) {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多选项",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 更多菜单
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("加入播放列表") },
                                onClick = {
                                    onAddToPlaylist()
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null
                                    )
                                }
                            )

                            if (showDownloadButton) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (downloadStatus) {
                                                is DownloadStatus.Completed -> "已下载"
                                                is DownloadStatus.InProgress -> "下载中..."
                                                is DownloadStatus.Failed -> "重新下载"
                                                else -> "下载"
                                            }
                                        )
                                    },
                                    onClick = {
                                        if (downloadStatus !is DownloadStatus.Completed &&
                                            downloadStatus !is DownloadStatus.InProgress) {
                                            onDownloadClick()
                                        }
                                        showMoreMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = downloadStatus !is DownloadStatus.Completed &&
                                             downloadStatus !is DownloadStatus.InProgress
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(
    artworkUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = title.trim().split(" ", limit = 2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString(separator = "")
        .takeIf { it.isNotBlank() }
        ?: "播客"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                error = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
