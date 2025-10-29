package com.opoojkk.podium.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Podcast
import com.opoojkk.podium.presentation.SubscriptionsUiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    onRefresh: () -> Unit,
    onAddSubscription: (String) -> Unit,
    onEditSubscription: (String, String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onClearDuplicateMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Custom TopAppBar without windowInsets to fix spacing issue
        TopAppBar(
            title = { Text("订阅") },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加订阅")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            windowInsets = WindowInsets(0.dp) // Fix spacing with status bar
        )
        
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.subscriptions.isEmpty()) {
                item {
                    Text(
                        text = "还没有订阅任何播客",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = state.subscriptions,
                    key = { index, podcast -> 
                        // Use podcast ID with date as primary key, with index as fallback for uniqueness
                        if (podcast.id.isNotBlank()) {
                            "${podcast.id}-${podcast.lastUpdated.toEpochMilliseconds()}"
                        } else {
                            // Fallback to index-based key if ID is empty
                            "podcast-$index"
                        }
                    }
                ) { index, podcast ->
                    SubscriptionCard(
                        podcast = podcast,
                        onEdit = { newTitle -> onEditSubscription(podcast.id, newTitle) },
                        onDelete = { onDeleteSubscription(podcast.id) },
                        onClick = { onPodcastClick(podcast) },
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddSubscriptionDialog(
            isLoading = state.isAdding,
            onDismiss = {
                if (!state.isAdding) {
                    showAddDialog = false
                }
            },
            onConfirm = { feedUrl ->
                onAddSubscription(feedUrl)
                // 不在这里关闭对话框，等待订阅完成后自动关闭
            }
        )
    }

    // 订阅完成后自动关闭对话框
    LaunchedEffect(state.isAdding, state.duplicateSubscriptionTitle) {
        if (showAddDialog && !state.isAdding && state.duplicateSubscriptionTitle == null) {
            // 只有在非加载状态且没有错误时才自动关闭
            showAddDialog = false
        }
    }
    
    // 显示重复订阅提示
    state.duplicateSubscriptionTitle?.let { title ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClearDuplicateMessage,
            title = { Text("已订阅该播客") },
            text = { Text("「$title」已在订阅列表中，已为您更新内容。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onClearDuplicateMessage) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun SubscriptionCard(
    podcast: Podcast,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(podcast.title) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧方形圆角图标
            PodcastArtwork(
                artworkUrl = podcast.artworkUrl,
                title = podcast.title,
                modifier = Modifier.size(64.dp)
            )
            
            // 右侧内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "最后更新: ${formatLastUpdated(podcast.lastUpdated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            // 更多菜单按钮
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑名称") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            editTitle = podcast.title
                            showEditDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除订阅") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑订阅名称") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val title = editTitle.trim()
                        if (title.isNotEmpty()) {
                            onEdit(title)
                            showEditDialog = false
                        }
                    },
                    enabled = editTitle.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除订阅") },
            text = { Text("确定要删除该订阅及其剧集吗？此操作不可撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var feedUrl by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("请输入播客的 RSS 链接:")
                androidx.compose.material3.OutlinedTextField(
                    value = feedUrl,
                    onValueChange = { feedUrl = it },
                    label = { Text("RSS 链接") },
                    placeholder = { Text("https://example.com/feed.xml") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                if (isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在解析 RSS...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (feedUrl.isNotBlank()) {
                        onConfirm(feedUrl.trim())
                    }
                },
                enabled = feedUrl.isNotBlank() && !isLoading
            ) {
                Text(if (isLoading) "添加中..." else "添加")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PodcastArtwork(
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            // 调试日志
            println("🖼️ Loading podcast artwork: $artworkUrl")
            
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    // 加载中显示占位符
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                error = { error ->
                    // 加载失败显示占位符
                    println("❌ Failed to load image: $artworkUrl, error: ${error.result.throwable?.message}")
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            )
        } else {
            // 没有图片URL时显示占位符
            println("⚠️ No artwork URL for podcast: $title")
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun formatLastUpdated(instant: kotlinx.datetime.Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
}
