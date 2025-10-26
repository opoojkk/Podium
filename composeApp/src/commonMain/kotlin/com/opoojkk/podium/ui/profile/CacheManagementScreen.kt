package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.presentation.ProfileUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    state: ProfileUiState,
    onBackClick: () -> Unit,
    onTogglePodcastAutoDownload: (String, Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 缓存信息卡片
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("缓存信息", style = MaterialTheme.typography.titleMedium)
                        ListItem(
                            headlineContent = { Text("已缓存内容") },
                            supportingContent = { Text("占用存储空间") },
                            leadingContent = { 
                                Icon(Icons.Default.Download, contentDescription = null)
                            },
                            trailingContent = {
                                Text(
                                    "${state.cacheSizeInMb} MB",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                        )
                    }
                }
            }

            // 清除缓存操作
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("管理操作", style = MaterialTheme.typography.titleMedium)
                        ListItem(
                            headlineContent = { Text("清除所有缓存") },
                            supportingContent = { Text("删除所有已下载的节目文件") },
                            leadingContent = { 
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            trailingContent = {
                                TextButton(onClick = onClearCache) {
                                    Text("清除")
                                }
                            },
                        )
                    }
                }
            }

            // 自动缓存设置标题
            item {
                Text(
                    "自动缓存设置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp)
                )
                Text(
                    "为每个播客单独设置自动缓存。开启后将下载该播客的所有节目，并在检测到更新时自动下载新节目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 播客列表
            if (state.subscribedPodcasts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "暂无订阅的播客",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            state.subscribedPodcasts.forEachIndexed { index, podcast ->
                                PodcastAutoDownloadItem(
                                    podcast = podcast,
                                    onToggleAutoDownload = { enabled ->
                                        onTogglePodcastAutoDownload(podcast.id, enabled)
                                    }
                                )
                                if (index < state.subscribedPodcasts.size - 1) {
                                    HorizontalDivider()
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
private fun PodcastAutoDownloadItem(
    podcast: Podcast,
    onToggleAutoDownload: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(podcast.title) },
        supportingContent = { 
            Text(
                if (podcast.autoDownload) "已启用自动缓存所有节目" 
                else "未启用自动缓存"
            ) 
        },
        trailingContent = {
            Switch(
                checked = podcast.autoDownload,
                onCheckedChange = onToggleAutoDownload
            )
        },
    )
}


