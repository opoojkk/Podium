package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Download
import androidx.compose.material3.icons.filled.FileUpload
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.Podcasts
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.presentation.ProfileUiState

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onToggleAutoDownload: (Boolean) -> Unit,
    onManageDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubscriptionCard(state)
        SettingsCard(
            state = state,
            onToggleAutoDownload = onToggleAutoDownload,
            onManageDownloads = onManageDownloads,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionCard(state: ProfileUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("已订阅播客", style = MaterialTheme.typography.titleMedium)
            if (state.subscribedPodcasts.isEmpty()) {
                Text(
                    text = "暂未订阅任何播客",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.subscribedPodcasts.forEach { podcast ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                text = podcast.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    state: ProfileUiState,
    onToggleAutoDownload: (Boolean) -> Unit,
    onManageDownloads: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("缓存设置", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("自动缓存最新一期") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                trailingContent = {
                    Switch(checked = state.autoDownload, onCheckedChange = onToggleAutoDownload)
                },
                supportingContent = { Text("启用后将自动下载每个播客的最新节目") },
            )
            ListItem(
                headlineContent = { Text("已缓存 ${state.cacheSizeInMb} MB") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = onManageDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "管理缓存")
                    }
                },
                supportingContent = { Text("手动管理下载或清理空间") },
            )
        }
    }
}
