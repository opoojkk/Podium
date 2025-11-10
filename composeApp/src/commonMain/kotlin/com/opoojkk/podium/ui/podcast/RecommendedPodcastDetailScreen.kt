package com.opoojkk.podium.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.opoojkk.podium.data.model.recommended.RecommendedPodcast
import com.opoojkk.podium.data.rss.PodcastFeed
import com.opoojkk.podium.data.rss.RssEpisode
import com.opoojkk.podium.ui.components.PodcastEpisodeCardSkeleton
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class EpisodeSortOrder {
    NEWEST_FIRST,  // 时间从晚到早（默认，最新的在前）
    OLDEST_FIRST   // 时间从早到晚（最老的在前）
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendedPodcastDetailScreen(
    podcast: RecommendedPodcast,
    onBack: () -> Unit,
    onSubscribe: (String) -> Unit,
    onPlayEpisode: (RssEpisode) -> Unit,
    loadPodcastFeed: suspend (String) -> Result<PodcastFeed>,
    modifier: Modifier = Modifier,
) {
    var feedState by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var isSubscribed by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(EpisodeSortOrder.NEWEST_FIRST) }

    LaunchedEffect(podcast.rssUrl) {
        if (!podcast.rssUrl.isNullOrBlank()) {
            feedState = FeedState.Loading
            val result = loadPodcastFeed(podcast.rssUrl)
            feedState = result.fold(
                onSuccess = { FeedState.Success(it) },
                onFailure = { FeedState.Error(it.message ?: "加载失败") }
            )
        } else {
            feedState = FeedState.Error("无效的RSS链接")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播客详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 播客信息头部
            item {
                PodcastHeader(
                    podcast = podcast,
                    feedState = feedState,
                    isSubscribed = isSubscribed,
                    onSubscribeClick = {
                        podcast.rssUrl?.let { rssUrl ->
                            onSubscribe(rssUrl)
                            isSubscribed = true
                        }
                    }
                )
            }

            // 单集列表
            when (val state = feedState) {
                is FeedState.Loading -> {
                    items(5) {
                        PodcastEpisodeCardSkeleton()
                    }
                }
                is FeedState.Success -> {
                    // 单集列表标题和排序按钮
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "单集列表",
                                style = MaterialTheme.typography.titleMedium,
                            )

                            // 排序按钮
                            FilterChip(
                                selected = false,
                                onClick = {
                                    sortOrder = when (sortOrder) {
                                        EpisodeSortOrder.NEWEST_FIRST -> EpisodeSortOrder.OLDEST_FIRST
                                        EpisodeSortOrder.OLDEST_FIRST -> EpisodeSortOrder.NEWEST_FIRST
                                    }
                                },
                                label = {
                                    Text(
                                        text = when (sortOrder) {
                                            EpisodeSortOrder.NEWEST_FIRST -> "时间从晚到早"
                                            EpisodeSortOrder.OLDEST_FIRST -> "时间从早到晚"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "排序",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // 根据排序顺序显示单集列表
                    val sortedEpisodes = when (sortOrder) {
                        EpisodeSortOrder.NEWEST_FIRST -> state.feed.episodes.sortedByDescending { it.publishDate }
                        EpisodeSortOrder.OLDEST_FIRST -> state.feed.episodes.sortedBy { it.publishDate }
                    }

                    items(sortedEpisodes, key = { it.id }) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            onClick = { onPlayEpisode(episode) }
                        )
                    }
                }
                is FeedState.Error -> {
                    item {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastHeader(
    podcast: RecommendedPodcast,
    feedState: FeedState,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed = (feedState as? FeedState.Success)?.feed

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                val artworkUrl = feed?.artworkUrl ?: podcast.artworkUrl
                val initials = podcast.name
                    .trim()
                    .split(" ", limit = 2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString(separator = "")
                    .takeIf { it.isNotBlank() }
                    ?: "播客"

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
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        error = {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    )
                } else {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 名称和订阅按钮
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = feed?.title ?: podcast.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Button(
                    onClick = onSubscribeClick,
                    enabled = !isSubscribed && !podcast.rssUrl.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubscribed) "已订阅" else "订阅")
                }
            }
        }

        // 描述
        Text(
            text = feed?.description ?: podcast.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 上次更新时间
        if (feed != null) {
            val lastUpdated = formatLastUpdated(feed.lastUpdated)
            Text(
                text = "上次更新：$lastUpdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: RssEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = episode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDate(episode.publishDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (episode.duration != null) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(episode.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatDate(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}小时${minutes % 60}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "${seconds}秒"
    }
}

private fun formatLastUpdated(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}年${localDateTime.monthNumber}月${localDateTime.dayOfMonth}日"
}

sealed interface FeedState {
    data object Loading : FeedState
    data class Success(val feed: PodcastFeed) : FeedState
    data class Error(val message: String) : FeedState
}
