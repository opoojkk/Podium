package com.opoojkk.podium.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.EpisodeWithPodcast
import com.opoojkk.podium.ui.components.PodcastEpisodeCard
import com.opoojkk.podium.platform.BackHandler

/**
 * 查看更多页面，展示完整的单集列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewMoreScreen(
    title: String,
    episodes: List<EpisodeWithPodcast>,
    onPlayEpisode: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    onPauseResume: () -> Unit = {},
    onAddToPlaylist: (String) -> Unit = {},
) {
    // 处理系统返回按钮
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (episodes.isEmpty()) {
                item {
                    Text(
                        text = "暂无内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(episodes, key = { it.episode.id }) { episodeWithPodcast ->
                    val isCurrentEpisode = episodeWithPodcast.episode.id == currentPlayingEpisodeId
                    PodcastEpisodeCard(
                        episodeWithPodcast = episodeWithPodcast,
                        onPlayClick = {
                            if (isCurrentEpisode) {
                                // 如果是当前播放的单集，切换播放/暂停
                                onPauseResume()
                            } else {
                                // 如果是其他单集，播放它
                                onPlayEpisode(episodeWithPodcast.episode)
                            }
                        },
                        onAddToPlaylist = { onAddToPlaylist(episodeWithPodcast.episode.id) },
                        isCurrentlyPlaying = isCurrentEpisode && isPlaying,
                        isBuffering = isCurrentEpisode && isBuffering,
                    )
                }
            }
        }
    }
}

