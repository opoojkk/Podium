package com.opoojkk.podium.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.presentation.HomeUiState
import com.opoojkk.podium.ui.components.PodcastEpisodeCard

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlayEpisode: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playedIds = state.recentPlayed.map { it.episode.id }.toSet()
    val updatesOnly = state.recentUpdates.filter { it.episode.id !in playedIds }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Section(title = "最近收听", description = "继续播放你喜欢的节目")
        }
        if (state.recentPlayed.isEmpty()) {
            item { EmptyHint(text = "暂无播放记录") }
        } else {
            items(state.recentPlayed, key = { it.episode.id }) { item ->
                PodcastEpisodeCard(
                    episodeWithPodcast = item,
                    onPlayClick = { onPlayEpisode(item.episode) },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Section(title = "最近更新", description = "及时了解最新节目")
        }
        if (updatesOnly.isEmpty()) {
            item { EmptyHint(text = "暂无新节目") }
        } else {
            items(updatesOnly, key = { it.episode.id }) { item ->
                PodcastEpisodeCard(
                    episodeWithPodcast = item,
                    onPlayClick = { onPlayEpisode(item.episode) },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
