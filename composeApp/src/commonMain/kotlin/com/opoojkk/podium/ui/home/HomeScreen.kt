package com.opoojkk.podium.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.recommended.RecommendedPodcast
import com.opoojkk.podium.presentation.HomeUiState
import com.opoojkk.podium.ui.components.HorizontalEpisodeRow
import com.opoojkk.podium.ui.components.HorizontalEpisodeRowSkeleton
import com.opoojkk.podium.ui.components.PodcastEpisodeCard
import com.opoojkk.podium.ui.components.PodcastEpisodeCardSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlayEpisode: (Episode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onViewMoreRecentPlayed: () -> Unit = {},
    onViewMoreRecentUpdates: () -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRecommendedPodcastClick: (RecommendedPodcast) -> Unit = {},
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    onPauseResume: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeSearchBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClear = onClearSearch,
                        isSearching = state.isSearching,
                    )
                    state.searchErrorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.isSearchActive) {
                item {
                    SectionHeader(
                        title = "搜索结果",
                        description = "快速查找你想听的节目",
                        onViewMore = null,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                when {
                    state.isSearching -> {
                        items(4) {
                            PodcastEpisodeCardSkeleton(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    state.searchResults.isEmpty() -> {
                        item {
                            EmptyHint(text = "没有找到相关内容")
                        }
                    }
                    else -> {
                        items(state.searchResults, key = { it.episode.id }) { item ->
                            PodcastEpisodeCard(
                                episodeWithPodcast = item,
                                onPlayClick = { onPlayEpisode(item.episode) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "最近收听",
                            description = "继续播放你喜欢的节目",
                            onViewMore = if (state.recentPlayed.isNotEmpty()) onViewMoreRecentPlayed else null,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        when {
                            state.isLoading -> {
                                HorizontalEpisodeRowSkeleton()
                            }
                            state.recentPlayed.isEmpty() -> {
                                EmptyHint(text = "暂无播放记录")
                            }
                            else -> {
                                HorizontalEpisodeRow(
                                    episodes = state.recentPlayed,
                                    onPlayClick = { onPlayEpisode(it.episode) },
                                )
                            }
                        }
                    }
                }

                // 推荐部分 - 显示来自 JSON 的精选播客
                if (state.recommendedPodcasts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "推荐",
                                description = "为你精选的优质播客",
                                onViewMore = null,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HorizontalRecommendedPodcastRow(
                                podcasts = state.recommendedPodcasts.take(10),
                                onPodcastClick = onRecommendedPodcastClick,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "最近更新",
                            description = "及时了解最新节目",
                            onViewMore = if (state.recentUpdates.isNotEmpty()) onViewMoreRecentUpdates else null,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        when {
                            state.isLoading -> {
                                repeat(3) {
                                    PodcastEpisodeCardSkeleton(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                            state.recentUpdates.isEmpty() -> {
                                EmptyHint(text = "暂无新节目")
                            }
                            else -> {
                                state.recentUpdates.take(3).forEach { item ->
                                    val isCurrentEpisode = item.episode.id == currentPlayingEpisodeId
                                    PodcastEpisodeCard(
                                        episodeWithPodcast = item,
                                        onPlayClick = {
                                            if (isCurrentEpisode) {
                                                // 如果是当前播放的单集，切换播放/暂停
                                                onPauseResume()
                                            } else {
                                                // 如果是其他单集，播放它
                                                onPlayEpisode(item.episode)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        compact = true,
                                        isCurrentlyPlaying = isCurrentEpisode && isPlaying,
                                    )
                                }
                            }
                        }
                    }
                }
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

@Composable
private fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
            )
        },
        trailingIcon = {
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                query.isNotEmpty() -> {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                        )
                    }
                }
            }
        },
        placeholder = { Text("搜索节目或播客") },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

/**
 * 横向滚动的推荐播客列表
 */
@Composable
private fun HorizontalRecommendedPodcastRow(
    podcasts: List<Pair<RecommendedPodcast, String>>,
    onPodcastClick: (RecommendedPodcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(podcasts, key = { it.first.id }) { (podcast, categoryName) ->
            HorizontalRecommendedPodcastCard(
                podcast = podcast,
                categoryName = categoryName,
                onClick = { onPodcastClick(podcast) },
            )
        }
    }
}

/**
 * 横向滚动的推荐播客卡片
 */
@Composable
private fun HorizontalRecommendedPodcastCard(
    podcast: RecommendedPodcast,
    categoryName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 播客封面
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val artworkUrl = podcast.artworkUrl
                val initials = podcast.name
                    .trim()
                    .split(" ", limit = 2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString(separator = "")
                    .takeIf { it.isNotBlank() }
                    ?: "播客"

                // 加载图片或显示占位符
                if (!artworkUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = artworkUrl,
                        contentDescription = podcast.name,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        error = {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    )
                } else {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 播客名称
            Text(
                text = podcast.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 分类标签
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // 播客描述
            Text(
                text = podcast.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
