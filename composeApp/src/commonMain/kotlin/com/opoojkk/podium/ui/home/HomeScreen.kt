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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
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
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.presentation.HomeUiState
import com.opoojkk.podium.presentation.SearchFilterType
import com.opoojkk.podium.ui.components.HorizontalEpisodeRow
import com.opoojkk.podium.ui.components.HorizontalEpisodeRowSkeleton
import com.opoojkk.podium.ui.components.PodcastEpisodeCard
import com.opoojkk.podium.ui.components.PodcastEpisodeCardSkeleton
import com.opoojkk.podium.util.Logger

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
    onPodcastClick: (Podcast) -> Unit = {},
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    onPauseResume: () -> Unit = {},
    onAddToPlaylist: (String) -> Unit = {},
    onEpisodeClick: (Episode) -> Unit = {},
    onLoadMoreSearchResults: () -> Unit = {},
    onSearchFilterTypeChange: (SearchFilterType) -> Unit = {},
) {
    // Debug: Print XYZRank data status
    Logger.d("HomeScreen") { "🏠 HomeScreen - XYZRank data: hotEpisodes=${state.hotEpisodes.size}, hotPodcasts=${state.hotPodcasts.size}, newEpisodes=${state.newEpisodes.size}, newPodcasts=${state.newPodcasts.size}" }

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
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(
                            title = "搜索结果",
                            description = "快速查找你想听的节目",
                            onViewMore = null,
                        )
                        // 搜索结果筛选 Tab
                        SearchFilterTabs(
                            selectedFilter = state.searchFilterType,
                            onFilterChange = onSearchFilterTypeChange,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
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
                        // 使用 derivedStateOf 优化过滤计算，避免不必要的重组
                        val filteredResults = remember(state.searchResults, state.searchFilterType) {
                            derivedStateOf {
                                when (state.searchFilterType) {
                                    SearchFilterType.ALL -> state.searchResults
                                    SearchFilterType.PODCASTS -> state.searchResults.filter { item ->
                                        item.episode.audioUrl.isEmpty() &&
                                        item.episode.id.startsWith("itunes_ep_") &&
                                        item.podcast.id.startsWith("itunes_")
                                    }
                                    SearchFilterType.EPISODES -> state.searchResults.filter { item ->
                                        !(item.episode.audioUrl.isEmpty() &&
                                          item.episode.id.startsWith("itunes_ep_") &&
                                          item.podcast.id.startsWith("itunes_"))
                                    }
                                }
                            }
                        }.value

                        items(filteredResults, key = { it.episode.id }) { item ->
                            // 判断是播客节目还是单集：如果 audioUrl 为空且是 iTunes 搜索结果，则认为是播客节目
                            val isPodcast = item.episode.audioUrl.isEmpty() &&
                                           item.episode.id.startsWith("itunes_ep_") &&
                                           item.podcast.id.startsWith("itunes_")

                            if (isPodcast) {
                                // 显示播客卡片（类似苹果播客的搜索结果样式）
                                SearchResultPodcastCard(
                                    podcast = item.podcast,
                                    description = item.episode.description,
                                    onClick = { onPodcastClick(item.podcast) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            } else {
                                // 显示单集卡片（保持原有样式）
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
                                    onClick = { onEpisodeClick(item.episode) },
                                    onAddToPlaylist = { onAddToPlaylist(item.episode.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    isCurrentlyPlaying = isCurrentEpisode && isPlaying,
                                    isBuffering = isCurrentEpisode && isBuffering,
                                )
                            }

                                            // 预加载：当滚动到倒数第3个元素时触发加载更多
                            val index = filteredResults.indexOf(item)
                            if (index == filteredResults.size - 3 && state.hasMoreSearchResults && !state.isLoadingMoreResults) {
                                LaunchedEffect(Unit) {
                                    onLoadMoreSearchResults()
                                }
                            }
                        }

                        // 加载更多指示器
                        if (state.isLoadingMoreResults) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
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
                                    onPlayClick = { episodeWithPodcast ->
                                        val isCurrentEpisode = episodeWithPodcast.episode.id == currentPlayingEpisodeId
                                        if (isCurrentEpisode) {
                                            // 如果是当前播放的单集，切换播放/暂停
                                            onPauseResume()
                                        } else {
                                            // 如果是其他单集，播放它
                                            onPlayEpisode(episodeWithPodcast.episode)
                                        }
                                    },
                                    onCardClick = { episodeWithPodcast ->
                                        onEpisodeClick(episodeWithPodcast.episode)
                                    },
                                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                )
                            }
                        }
                    }
                }

                // 热门节目 - 来自 XYZRank
                if (state.hotEpisodes.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "热门节目",
                                description = "当下最受欢迎的播客节目",
                                onViewMore = null,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HorizontalEpisodeRow(
                                episodes = state.hotEpisodes.take(10),
                                onPlayClick = { episodeWithPodcast ->
                                    val isCurrentEpisode = episodeWithPodcast.episode.id == currentPlayingEpisodeId
                                    if (isCurrentEpisode) {
                                        onPauseResume()
                                    } else {
                                        onPlayEpisode(episodeWithPodcast.episode)
                                    }
                                },
                                onCardClick = { episodeWithPodcast ->
                                    onEpisodeClick(episodeWithPodcast.episode)
                                },
                                currentPlayingEpisodeId = currentPlayingEpisodeId,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                            )
                        }
                    }
                }

                // 热门播客 - 来自 XYZRank
                if (state.hotPodcasts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "热门播客",
                                description = "高人气的优质播客推荐",
                                onViewMore = null,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HorizontalPodcastRow(
                                podcasts = state.hotPodcasts.take(10),
                                onPodcastClick = onPodcastClick,
                            )
                        }
                    }
                }

                // 新锐节目 - 来自 XYZRank
                if (state.newEpisodes.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "新锐节目",
                                description = "新鲜出炉的精彩节目",
                                onViewMore = null,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HorizontalEpisodeRow(
                                episodes = state.newEpisodes.take(10),
                                onPlayClick = { episodeWithPodcast ->
                                    val isCurrentEpisode = episodeWithPodcast.episode.id == currentPlayingEpisodeId
                                    if (isCurrentEpisode) {
                                        onPauseResume()
                                    } else {
                                        onPlayEpisode(episodeWithPodcast.episode)
                                    }
                                },
                                onCardClick = { episodeWithPodcast ->
                                    onEpisodeClick(episodeWithPodcast.episode)
                                },
                                currentPlayingEpisodeId = currentPlayingEpisodeId,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                            )
                        }
                    }
                }

                // 新锐播客 - 来自 XYZRank
                if (state.newPodcasts.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(
                                title = "新锐播客",
                                description = "值得关注的新兴播客",
                                onViewMore = null,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HorizontalPodcastRow(
                                podcasts = state.newPodcasts.take(10),
                                onPodcastClick = onPodcastClick,
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
                                        onClick = { onEpisodeClick(item.episode) },
                                        onAddToPlaylist = { onAddToPlaylist(item.episode.id) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        compact = true,
                                        isCurrentlyPlaying = isCurrentEpisode && isPlaying,
                                        isBuffering = isCurrentEpisode && isBuffering,
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
 * 横向滚动的播客列表 - 用于显示XYZRank播客数据
 */
@Composable
private fun HorizontalPodcastRow(
    podcasts: List<Podcast>,
    onPodcastClick: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(podcasts, key = { it.id }) { podcast ->
            PodcastCard(
                podcast = podcast,
                onClick = { onPodcastClick(podcast) }
            )
        }
    }
}

/**
 * 播客卡片
 */
@Composable
private fun PodcastCard(
    podcast: Podcast,
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
            ArtworkWithPlaceholder(
                artworkUrl = podcast.artworkUrl,
                title = podcast.title,
                size = 136.dp,
                contentDescription = podcast.title
            )

            // 播客名称
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 播客描述
            Text(
                text = podcast.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 搜索结果中的播客卡片（与 PodcastEpisodeCard 风格一致）
 */
@Composable
private fun SearchResultPodcastCard(
    podcast: Podcast,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：播客封面
            ArtworkWithPlaceholder(
                artworkUrl = podcast.artworkUrl,
                title = podcast.title,
                size = 80.dp,
                contentDescription = podcast.title
            )

            // 右侧：播客信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 播客名称
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 播客描述（显示类型、数量等信息）
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 搜索结果筛选 Tab
 */
@Composable
private fun SearchFilterTabs(
    selectedFilter: SearchFilterType,
    onFilterChange: (SearchFilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = when (selectedFilter) {
            SearchFilterType.ALL -> 0
            SearchFilterType.PODCASTS -> 1
            SearchFilterType.EPISODES -> 2
        },
        modifier = modifier,
        containerColor = Color.Transparent,
        indicator = { tabPositions ->
            if (tabPositions.isNotEmpty()) {
                val currentTabPosition = when (selectedFilter) {
                    SearchFilterType.ALL -> tabPositions[0]
                    SearchFilterType.PODCASTS -> tabPositions[1]
                    SearchFilterType.EPISODES -> tabPositions[2]
                }
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(currentTabPosition),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Tab(
            selected = selectedFilter == SearchFilterType.ALL,
            onClick = { onFilterChange(SearchFilterType.ALL) },
            text = {
                Text(
                    text = "全部",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        )
        Tab(
            selected = selectedFilter == SearchFilterType.PODCASTS,
            onClick = { onFilterChange(SearchFilterType.PODCASTS) },
            text = {
                Text(
                    text = "播客节目",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        )
        Tab(
            selected = selectedFilter == SearchFilterType.EPISODES,
            onClick = { onFilterChange(SearchFilterType.EPISODES) },
            text = {
                Text(
                    text = "单集",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        )
    }
}
