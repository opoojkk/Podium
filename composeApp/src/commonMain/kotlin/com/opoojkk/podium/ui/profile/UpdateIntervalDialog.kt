package com.opoojkk.podium.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.UpdateInterval

@Composable
fun UpdateIntervalDialog(
    currentInterval: UpdateInterval,
    onIntervalSelected: (UpdateInterval) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新频率") },
        text = {
            Column {
                UpdateInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntervalSelected(interval) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = interval == currentInterval,
                            onClick = { onIntervalSelected(interval) }
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = interval.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (interval) {
                                    UpdateInterval.EVERY_TIME -> "每次打开应用时更新"
                                    UpdateInterval.DAILY -> "距离上次更新超过一天时自动更新"
                                    UpdateInterval.MANUAL -> "仅手动刷新时更新"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
