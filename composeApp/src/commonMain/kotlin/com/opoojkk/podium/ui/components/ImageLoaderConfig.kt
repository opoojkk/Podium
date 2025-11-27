package com.opoojkk.podium.ui.components

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.FileSystem

/**
 * 图片加载器配置 - 实现三级缓存策略
 *
 * 三级缓存策略：
 * 1. 内存缓存（一级缓存）：快速访问最近使用的图片，减少解码次数
 * 2. 磁盘缓存（二级缓存）：持久化存储已下载的图片，避免重复网络请求
 * 3. 网络加载（三级缓存）：从网络获取图片并自动存入缓存
 *
 * 缓存查找顺序：内存 -> 磁盘 -> 网络
 */
object ImageLoaderConfig {

    /**
     * 创建配置好三级缓存的图片加载器
     *
     * @param context 平台上下文
     * @param debug 是否启用调试日志
     * @return 配置好的ImageLoader实例
     */
    fun createImageLoader(
        context: PlatformContext,
        debug: Boolean = false
    ): ImageLoader {
        return ImageLoader.Builder(context)
            // 一级缓存：内存缓存配置
            .memoryCache {
                MemoryCache.Builder()
                    // 设置内存缓存大小为可用内存的25%
                    .maxSizePercent(context, 0.25)
                    // 启用强引用缓存，保持最近使用的图片
                    .strongReferencesEnabled(true)
                    .build()
            }
            // 二级缓存：磁盘缓存配置
            .diskCache {
                newDiskCache(context)
            }
            // 启用淡入动画，提升用户体验
            .crossfade(true)
            .build()
    }

    /**
     * 创建磁盘缓存
     *
     * 磁盘缓存策略：
     * - 最大缓存大小：512MB
     * - 缓存目录：应用专用缓存目录
     * - 自动清理：达到上限时自动删除最旧的缓存
     */
    private fun newDiskCache(context: PlatformContext): DiskCache {
        return DiskCache.Builder()
            .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
            .maxSizeBytes(512L * 1024 * 1024) // 512MB
            .build()
    }

    /**
     * 清除所有缓存（内存 + 磁盘）
     */
    fun clearCache(imageLoader: ImageLoader) {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }

    /**
     * 清除内存缓存
     */
    fun clearMemoryCache(imageLoader: ImageLoader) {
        imageLoader.memoryCache?.clear()
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(imageLoader: ImageLoader): CacheStats {
        val memorySize = imageLoader.memoryCache?.size ?: 0
        val diskSize = imageLoader.diskCache?.size ?: 0
        return CacheStats(
            memoryCacheSize = memorySize,
            diskCacheSize = diskSize
        )
    }
}

/**
 * 缓存统计信息
 */
data class CacheStats(
    val memoryCacheSize: Long,
    val diskCacheSize: Long
)
