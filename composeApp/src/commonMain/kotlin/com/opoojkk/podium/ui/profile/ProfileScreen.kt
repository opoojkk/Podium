package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.presentation.ProfileUiState

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onCacheManagementClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 缓存管理入口
        ListItem(
            headlineContent = { Text("缓存管理") },
            supportingContent = {
                val cachedCount = state.cachedDownloads.size
                val downloadingCount = state.inProgressDownloads.size
                val queuedCount = state.queuedDownloads.size
                val description = buildString {
                    append("已缓存 ${state.cacheSizeInMb} MB")
                    if (cachedCount > 0) {
                        append(" · 已缓存 $cachedCount 项")
                    }
                    if (downloadingCount > 0) {
                        append(" · 正在缓存 $downloadingCount 项")
                    }
                    if (queuedCount > 0) {
                        append(" · 排队 $queuedCount 项")
                    }
                }
                Text(description)
            },
            leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCacheManagementClick() },
        )
        
        ListItem(
            headlineContent = { Text("导入订阅 (OPML)") },
            supportingContent = { Text("支持从其他客户端导入 RSS 列表") },
            leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth(),
            trailingContent = {
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Default.FileUpload, contentDescription = "导入")
                }
            },
        )
        ListItem(
            headlineContent = { Text("导出订阅") },
            supportingContent = { Text("生成 OPML 文件，方便备份") },
            leadingContent = { Icon(Icons.Default.Podcasts, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Default.FileUpload, contentDescription = "导出")
                }
            },
        )
        ListItem(
            headlineContent = { Text("关于 Podium") },
            supportingContent = { Text("Compose Multiplatform 播客客户端示例") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
