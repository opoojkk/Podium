package com.opoojkk.podium.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackDetailControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    seekBackSeconds: Int = 10,
    seekForwardSeconds: Int = 30,
    sizing: PlaybackControlSizing = PlaybackControlDefaults.Default,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    onSpeedChange: (() -> Unit)? = null,
    sleepTimerMinutes: Int? = null,
    onSleepTimerClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 主控制区：播放控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeekButton(
                label = "-${seekBackSeconds}s",
                iconSize = sizing.seekIconSize,
                buttonSize = sizing.seekButtonSize,
                onClick = onSeekBack,
            )

            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                buttonSize = sizing.playButtonSize,
                iconSize = sizing.playIconSize,
                isBuffering = isBuffering,
            )

            SeekButton(
                label = "+${seekForwardSeconds}s",
                iconSize = sizing.seekIconSize,
                buttonSize = sizing.seekButtonSize,
                onClick = onSeekForward,
                isForward = true,
            )
        }

        // 副控制区：睡眠定时和倍速（始终显示）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 睡眠定时
            SleepTimerButton(
                minutes = sleepTimerMinutes,
                onClick = onSleepTimerClick ?: {},
            )

            // 倍速
            SpeedButton(
                speed = playbackSpeed,
                onClick = onSpeedChange ?: {},
            )
        }

        extraContent?.invoke(this)
    }
}

object PlaybackControlDefaults {
    val Default = PlaybackControlSizing(
        seekButtonSize = 68.dp,
        playButtonSize = 80.dp,
        seekIconSize = 32.dp,
        playIconSize = 40.dp,
        controlButtonSize = 64.dp,
        controlIconSize = 24.dp,
    )

    val Compact = PlaybackControlSizing(
        seekButtonSize = 60.dp,
        playButtonSize = 72.dp,
        seekIconSize = 28.dp,
        playIconSize = 36.dp,
        controlButtonSize = 56.dp,
        controlIconSize = 20.dp,
    )
}

data class PlaybackControlSizing(
    val seekButtonSize: Dp,
    val playButtonSize: Dp,
    val seekIconSize: Dp,
    val playIconSize: Dp,
    val controlButtonSize: Dp,
    val controlIconSize: Dp,
)

// 跳转按钮 - 扁平透明设计，只显示图标
@Composable
private fun SeekButton(
    label: String,
    iconSize: Dp,
    buttonSize: Dp,
    onClick: () -> Unit,
    isForward: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = if (isForward) Icons.Default.Forward30 else Icons.Default.Replay10,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// 播放/暂停按钮 - 大圆形填充设计
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    isBuffering: Boolean = false,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        enabled = !isBuffering,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// 睡眠定时按钮 - 简洁圆角设计
@Composable
private fun SleepTimerButton(
    minutes: Int?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (minutes != null) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (minutes != null) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "睡眠定时",
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (minutes != null) "${minutes}分" else "睡眠定时",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// 倍速按钮 - 简洁圆角设计
@Composable
private fun SpeedButton(
    speed: Float,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "播放速度",
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${speed}x",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
