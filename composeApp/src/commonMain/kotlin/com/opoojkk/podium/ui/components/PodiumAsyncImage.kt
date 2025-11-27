package com.opoojkk.podium.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Podium 图片加载组件 - 实现三级缓存策略
 *
 * 三级缓存策略（由Coil框架自动处理）：
 * 1. 内存缓存（一级）：优先从内存中读取
 * 2. 磁盘缓存（二级）：内存未命中时从磁盘读取
 * 3. 网络加载（三级）：缓存未命中时从网络加载并自动缓存
 *
 * 优化特性：
 * - 不强制压缩图片，由Coil自动根据需要优化
 * - 支持淡入动画，提升视觉体验
 * - 智能占位符，包含加载和错误状态
 * - 自动缓存管理，减少内存和网络消耗
 *
 * @param model 图片URL或资源
 * @param contentDescription 无障碍描述
 * @param modifier Compose modifier
 * @param contentScale 图片缩放方式
 * @param cornerRadius 圆角半径
 * @param showLoadingPlaceholder 是否显示加载占位符
 * @param showErrorPlaceholder 是否显示错误占位符
 * @param loading 自定义加载占位符（可选）
 * @param error 自定义错误占位符（可选）
 */
@Composable
fun PodiumAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp,
    showLoadingPlaceholder: Boolean = true,
    showErrorPlaceholder: Boolean = true,
    loading: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(model ?: "")
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = if (loading != null) {
            { loading() }
        } else if (showLoadingPlaceholder) {
            {
                ImagePlaceholder.Loading(
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = cornerRadius
                )
            }
        } else null,
        error = if (error != null) {
            { error() }
        } else if (showErrorPlaceholder) {
            {
                ImagePlaceholder.Error(
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = cornerRadius
                )
            }
        } else null
    )
}

/**
 * 带首字母占位符的图片加载组件
 *
 * 当图片加载失败或URL为空时，显示标题首字母作为占位符
 *
 * @param imageUrl 图片URL，可为null或空
 * @param title 标题，用于生成首字母
 * @param modifier Compose modifier
 * @param contentScale 图片缩放方式
 * @param cornerRadius 圆角半径
 * @param contentDescription 无障碍描述
 */
@Composable
fun PodiumImageWithInitials(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = title,
) {
    // 缓存首字母计算结果
    val initials = remember(title) {
        derivedStateOf {
            ImagePlaceholder.generateInitials(title)
        }
    }.value

    if (!imageUrl.isNullOrBlank()) {
        PodiumAsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            cornerRadius = cornerRadius,
            error = {
                ImagePlaceholder.Initials(
                    initials = initials,
                    modifier = Modifier.matchParentSize(),
                    cornerRadius = cornerRadius
                )
            }
        )
    } else {
        ImagePlaceholder.Initials(
            initials = initials,
            modifier = modifier,
            cornerRadius = cornerRadius
        )
    }
}
