package com.opoojkk.podium.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.navigation.PodiumDestination

/**
 * Material3风格的可收起/展开桌面侧边导航栏
 */
@Composable
fun DesktopNavigationRail(
    currentDestination: PodiumDestination,
    onNavigate: (PodiumDestination) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 宽度动画
    val targetWidth = if (isExpanded) 240.dp else 72.dp
    val width by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navigationRailWidth"
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(width),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // 切换按钮
            FilledTonalIconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .then(
                        if (isExpanded) Modifier.fillMaxWidth().height(56.dp)
                        else Modifier.size(56.dp)
                    ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
                ) {
                    Row(
                        modifier = if (isExpanded) Modifier.padding(horizontal = 16.dp) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            modifier = Modifier.size(24.dp)
                        )

                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Text(
                                text = "Podium",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 导航项
            PodiumDestination.entries.forEach { destination ->
                val selected = destination == currentDestination

                Surface(
                    modifier = Modifier
                        .then(
                            if (isExpanded) Modifier.fillMaxWidth().height(52.dp)
                            else Modifier.size(52.dp)
                        ),
                    color = if (selected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.large,
                    onClick = { onNavigate(destination) },
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = if (selected)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

