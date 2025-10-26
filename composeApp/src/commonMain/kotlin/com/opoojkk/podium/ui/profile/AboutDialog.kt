package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.platform.openUrl

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "关于 Podium",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 项目描述卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Podium - 跨平台播客播放器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "一个现代化的跨平台播客播放器，使用 Kotlin Multiplatform 和 Compose Multiplatform 技术构建。它采用单一代码库实现多平台支持，提供了一致且原生的用户体验。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
                        )
                    }
                }
                
                // 项目信息列表
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // GitHub 地址
                    AboutItem(
                        icon = Icons.Default.Storage,
                        title = "GitHub 仓库",
                        content = "github.com/opoojkk/podium",
                        onClick = { onOpenUrl("https://github.com/opoojkk/podium") }
                    )
                    
                    // 开源协议
                    AboutItem(
                        icon = Icons.Default.Code,
                        title = "开源协议",
                        content = "GNU General Public License v3.0",
                        onClick = { onOpenUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
                    )
                    
                    // 反馈
                    AboutItem(
                        icon = Icons.Default.Link,
                        title = "问题反馈",
                        content = "GitHub Issues",
                        onClick = { onOpenUrl("https://github.com/opoojkk/podium/issues") }
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun AboutItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (onClick != null) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (onClick != null) 2.dp else 0.dp
        ),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (onClick != null) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (onClick != null) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onClick != null) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (onClick != null) 
                        TextDecoration.Underline 
                    else 
                        TextDecoration.None
                )
            }
            
            if (onClick != null) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Link,
                    contentDescription = "打开链接",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
