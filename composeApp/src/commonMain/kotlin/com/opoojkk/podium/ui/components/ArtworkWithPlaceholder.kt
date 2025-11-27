package com.opoojkk.podium.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Displays podcast artwork with an intelligent placeholder fallback.
 *
 * 使用新的PodiumImageWithInitials组件，支持：
 * - 三级缓存策略（内存、磁盘、网络）
 * - 美观的加载动画
 * - 智能首字母占位符
 * - 不强制压缩图片
 *
 * @param artworkUrl URL of the artwork image, can be null or empty
 * @param title Title used to generate initials for the placeholder
 * @param size Size of the artwork box
 * @param modifier Additional modifier for the component
 * @param cornerRadius Corner radius for the artwork box
 * @param contentDescription Accessibility description for the image
 */
@Composable
fun ArtworkWithPlaceholder(
    artworkUrl: String?,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = title,
) {
    PodiumImageWithInitials(
        imageUrl = artworkUrl,
        title = title,
        modifier = modifier.size(size),
        contentScale = ContentScale.Crop,
        cornerRadius = cornerRadius,
        contentDescription = contentDescription
    )
}
