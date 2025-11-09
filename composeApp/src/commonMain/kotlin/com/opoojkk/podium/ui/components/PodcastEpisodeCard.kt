package com.opoojkk.podium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    showDownloadButton: Boolean = downloadStatus != null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkPlaceholder(
                artworkUrl = episodeWithPodcast.podcast.artworkUrl,
                title = episodeWithPodcast.podcast.title,
                modifier = Modifier.size(80.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = episodeWithPodcast.episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episodeWithPodcast.podcast.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episodeWithPodcast.episode.publishDate
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                        .toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onPlayClick) {
                        Text("播放")
                    }
                    // 只在订阅页面显示下载按钮
                    if (showDownloadButton) {
                        DownloadButton(
                            downloadStatus = downloadStatus,
                            onDownloadClick = onDownloadClick,
                            iconOnly = true
                        )
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
