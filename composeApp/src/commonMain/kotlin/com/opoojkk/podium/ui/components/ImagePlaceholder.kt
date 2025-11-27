package com.opoojkk.podium.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图片加载占位符组件
 *
 * 提供多种美观的占位符样式：
 * 1. 脉动动画的加载占位符
 * 2. 渐变色背景的错误占位符
 * 3. 文字初始化占位符
 */
object ImagePlaceholder {

    /**
     * 加载中占位符 - 使用脉动动画效果
     *
     * @param modifier Modifier
     * @param cornerRadius 圆角半径
     */
    @Composable
    fun Loading(
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 12.dp
    ) {
        // 创建无限循环的脉动动画
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

        // 透明度动画：0.3 <-> 0.6
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        // 缩放动画：0.95 <-> 1.0
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                    )
                )
                .scale(scale)
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            // 可选：添加一个小的加载指示器
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        CircleShape
                    )
            )
        }
    }

    /**
     * 错误占位符 - 显示错误图标
     *
     * @param modifier Modifier
     * @param cornerRadius 圆角半径
     */
    @Composable
    fun Error(
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 12.dp
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = "加载失败",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
            )
        }
    }

    /**
     * 文字初始化占位符 - 显示标题首字母
     *
     * @param initials 首字母文本
     * @param modifier Modifier
     * @param cornerRadius 圆角半径
     * @param backgroundColor 背景颜色
     */
    @Composable
    fun Initials(
        initials: String,
        modifier: Modifier = Modifier,
        cornerRadius: Dp = 12.dp,
        backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.8f),
                            backgroundColor,
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    /**
     * 从标题生成首字母
     *
     * @param title 标题文本
     * @return 首字母字符串，如果无法生成则返回"播客"
     */
    fun generateInitials(title: String): String {
        return title.trim()
            .split(" ", limit = 2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .take(2)
            .joinToString("")
            .takeIf { it.isNotBlank() }
            ?: "播客"
    }
}
