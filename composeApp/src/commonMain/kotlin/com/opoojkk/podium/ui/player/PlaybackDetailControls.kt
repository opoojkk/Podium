package com.opoojkk.podium.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackDetailControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    seekBackSeconds: Int = 15,
    seekForwardSeconds: Int = 30,
    sizing: PlaybackControlSizing = PlaybackControlDefaults.Default,
    extraContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .heightIn(min = sizing.playButtonSize),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LargeSeekButton(
            label = "-${seekBackSeconds}s",
            iconSize = sizing.seekIconSize,
            buttonSize = sizing.seekButtonSize,
            onClick = onSeekBack,
        )

        LargePlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            buttonSize = sizing.playButtonSize,
            iconSize = sizing.playIconSize,
        )

        LargeSeekButton(
            label = "+${seekForwardSeconds}s",
            iconSize = sizing.seekIconSize,
            buttonSize = sizing.seekButtonSize,
            onClick = onSeekForward,
            isForward = true,
        )

        extraContent?.invoke(this)
    }
}

object PlaybackControlDefaults {
    val Default = PlaybackControlSizing(
        seekButtonSize = 80.dp,
        playButtonSize = 96.dp,
        seekIconSize = 28.dp,
        playIconSize = 48.dp,
    )

    val Compact = PlaybackControlSizing(
        seekButtonSize = 68.dp,
        playButtonSize = 84.dp,
        seekIconSize = 24.dp,
        playIconSize = 40.dp,
    )
}

data class PlaybackControlSizing(
    val seekButtonSize: Dp,
    val playButtonSize: Dp,
    val seekIconSize: Dp,
    val playIconSize: Dp,
)

@Composable
private fun LargeSeekButton(
    label: String,
    iconSize: Dp,
    buttonSize: Dp,
    onClick: () -> Unit,
    isForward: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isForward) Icons.Default.Forward30 else Icons.Default.Replay10,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LargePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(iconSize)
        )
    }
}
