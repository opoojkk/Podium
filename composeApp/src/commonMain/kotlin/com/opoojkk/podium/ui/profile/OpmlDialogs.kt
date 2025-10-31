package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.repository.PodcastRepository

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportOpmlDialog(
    opmlText: String,
    onOpmlTextChange: (String) -> Unit,
    isProcessing: Boolean,
    result: PodcastRepository.OpmlImportResult?,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPickFile: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("导入订阅") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "粘贴或输入订阅文件内容。支持 OPML 和 JSON 格式。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // File picker button (if available)
                if (onPickFile != null) {
                    TextButton(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("从文件选择")
                    }
                }

                OutlinedTextField(
                    value = opmlText,
                    onValueChange = onOpmlTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    label = { Text("订阅内容 (OPML/JSON)") },
                    singleLine = false,
                    maxLines = 12,
                )
                if (isProcessing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在导入订阅…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                result?.let {
                    ImportResultSummary(result = it)
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = opmlText.isNotBlank() && !isProcessing,
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (result != null && errorMessage.isNullOrBlank()) "完成" else "取消")
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportOpmlDialog(
    isProcessing: Boolean,
    opmlContent: String?,
    errorMessage: String?,
    selectedFormat: PodcastRepository.ExportFormat,
    onFormatChange: (PodcastRepository.ExportFormat) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: (String) -> Boolean,
    onSaveToFile: ((String) -> Unit)? = null,
) {
    var copyFeedback by remember(opmlContent) { mutableStateOf<CopyFeedback?>(null) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("导出订阅") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Format selector
                ExposedDropdownMenuBox(
                    expanded = formatMenuExpanded,
                    onExpandedChange = { formatMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = when (selectedFormat) {
                            PodcastRepository.ExportFormat.OPML -> "OPML"
                            PodcastRepository.ExportFormat.JSON -> "JSON"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("导出格式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true),
                    )
                    ExposedDropdownMenu(
                        expanded = formatMenuExpanded,
                        onDismissRequest = { formatMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("OPML (标准格式)") },
                            onClick = {
                                onFormatChange(PodcastRepository.ExportFormat.OPML)
                                formatMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("JSON (包含更多元数据)") },
                            onClick = {
                                onFormatChange(PodcastRepository.ExportFormat.JSON)
                                formatMenuExpanded = false
                            },
                        )
                    }
                }

                when {
                    isProcessing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "正在生成导出文件…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    opmlContent != null -> {
                        Text(
                            text = "复制以下文本即可在其他客户端导入订阅。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    Text(
                                        text = opmlContent,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        copyFeedback?.let { feedback ->
                            Text(
                                text = feedback.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (feedback.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "暂无可导出的订阅。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isProcessing && errorMessage == null && opmlContent != null) {
                    // Save to file button (if available)
                    if (onSaveToFile != null) {
                        TextButton(
                            onClick = { onSaveToFile(opmlContent) },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text("保存到文件")
                        }
                    }

                    // Copy button
                    TextButton(
                        onClick = {
                            val success = onCopy(opmlContent)
                            copyFeedback = if (success) {
                                CopyFeedback("已复制到剪贴板。", isError = false)
                            } else {
                                CopyFeedback("复制失败，请手动选择并复制上方文本。", isError = true)
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text("复制")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                ) {
                    Text("关闭")
                }
            }
        },
        dismissButton = {
            if (!isProcessing && errorMessage != null) {
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        },
    )
}

@Composable
private fun ImportResultSummary(result: PodcastRepository.OpmlImportResult) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (result.imported == 0 && result.skipped == 0 && result.failures.isEmpty()) {
            Text(
                text = "未在 OPML 内容中检测到可导入的 RSS 订阅。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "导入成功 ${result.imported} 项，跳过 ${result.skipped} 项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (result.failures.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "以下订阅导入失败（共 ${result.failures.size} 项）：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            val preview = result.failures.take(5)
            preview.forEach { failure ->
                val reasonSuffix = failure.reason?.takeIf { it.isNotBlank() }?.let { " (${it})" } ?: ""
                Text(
                    text = "- ${failure.feedUrl}$reasonSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val remaining = result.failures.size - preview.size
            if (remaining > 0) {
                Text(
                    text = "- 另外 $remaining 项未显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class CopyFeedback(
    val message: String,
    val isError: Boolean,
)
