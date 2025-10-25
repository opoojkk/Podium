package com.opoojkk.podium.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.presentation.HomeUiState
import com.opoojkk.podium.ui.components.HorizontalEpisodeRow
import com.opoojkk.podium.ui.components.PodcastEpisodeCard

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlayEpisode: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    onViewMoreRecentPlayed: () -> Unit = {},
    onViewMoreRecentUpdates: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 最近收听部分 - 横向滚动
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "最近收听",
                    description = "继续播放你喜欢的节目",
                    onViewMore = if (state.recentPlayed.isNotEmpty()) onViewMoreRecentPlayed else null,
                )
                if (state.recentPlayed.isEmpty()) {
                    EmptyHint(text = "暂无播放记录")
                } else {
                    HorizontalEpisodeRow(
                        episodes = state.recentPlayed,
                        onPlayClick = { onPlayEpisode(it.episode) },
                    )
                }
            }
        }

        // 最近更新部分 - 列表显示
        item {
            SectionHeader(
                title = "最近更新",
                description = "及时了解最新节目",
                onViewMore = if (state.recentUpdates.isNotEmpty()) onViewMoreRecentUpdates else null,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.recentUpdates.isEmpty()) {
            item {
                EmptyHint(text = "暂无新节目")
            }
        } else {
            items(state.recentUpdates, key = { it.episode.id }) { item ->
                PodcastEpisodeCard(
                    episodeWithPodcast = item,
                    onPlayClick = { onPlayEpisode(item.episode) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    description: String,
    onViewMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onViewMore != null) {
                TextButton(onClick = onViewMore) {
                    Text("查看更多")
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
    )
}
