package com.opoojkk.podium.player.ios

import com.opoojkk.podium.data.model.Episode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.MediaPlayer.*
import platform.UIKit.UIApplication
import platform.darwin.NSObject

/**
 * 管理播放音频时的媒体通知栏(iOS Now Playing Center)
 */
@OptIn(ExperimentalForeignApi::class)
class MediaNotificationManager(
    private val onPlayPause: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onSeekBackward: () -> Unit,
    private val onStop: () -> Unit
) {
    private val nowPlayingInfoCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()

    companion object {
        // Intent extra for opening player detail
        const val EXTRA_SHOW_PLAYER_DETAIL = "show_player_detail"
    }

    init {
        setupRemoteCommands()
    }

    /**
     * 设置远程控制命令
     */
    private fun setupRemoteCommands() {
        // 播放/暂停命令
        commandCenter.playCommand.enabled = true
        commandCenter.playCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Play command received")
            onPlayPause()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.pauseCommand.enabled = true
        commandCenter.pauseCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Pause command received")
            onPlayPause()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.togglePlayPauseCommand.enabled = true
        commandCenter.togglePlayPauseCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Toggle play/pause command received")
            onPlayPause()
            MPRemoteCommandHandlerStatusSuccess
        }

        // 快进/快退命令
        commandCenter.skipForwardCommand.enabled = true
        commandCenter.skipForwardCommand.preferredIntervals = listOf(15) // 15秒
        commandCenter.skipForwardCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Skip forward command received")
            onSeekForward()
            MPRemoteCommandHandlerStatusSuccess
        }

        commandCenter.skipBackwardCommand.enabled = true
        commandCenter.skipBackwardCommand.preferredIntervals = listOf(15) // 15秒
        commandCenter.skipBackwardCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Skip backward command received")
            onSeekBackward()
            MPRemoteCommandHandlerStatusSuccess
        }

        // 停止命令
        commandCenter.stopCommand.enabled = true
        commandCenter.stopCommand.addTargetWithHandler { _ ->
            println("🔔 iOS MediaNotificationManager: Stop command received")
            onStop()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    /**
     * 显示或更新媒体通知
     */
    fun showNotification(
        episode: Episode,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long?,
        isBuffering: Boolean = false
    ) {
        println("🔔 iOS MediaNotificationManager: 尝试显示通知 - ${episode.title}, isPlaying=$isPlaying, isBuffering=$isBuffering")

        val nowPlayingInfo = mutableMapOf<Any?, Any?>()

        // 设置标题和艺术家
        nowPlayingInfo[MPMediaItemPropertyTitle] = episode.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = episode.podcastTitle
        nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = "播客"

        // 设置播放时间
        if (durationMs != null && durationMs > 0) {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = durationMs / 1000.0
            nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = positionMs / 1000.0
        }

        // 设置播放速率（0 = 暂停，1 = 正常播放）
        // 当缓冲时，也设置为0以显示暂停状态
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying && !isBuffering) 1.0 else 0.0

        // TODO: 加载封面图片 (需要在后台线程异步加载)
        // episode.imageUrl?.let { imageUrl ->
        //     loadArtwork(imageUrl) { artwork ->
        //         artwork?.let {
        //             nowPlayingInfo[MPMediaItemPropertyArtwork] = it
        //             nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
        //             println("✅ iOS MediaNotificationManager: 通知已更新（含封面）")
        //         }
        //     }
        // }

        // 立即更新基本信息
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
        println("✅ iOS MediaNotificationManager: 通知已更新")

        // 根据缓冲状态启用/禁用播放/暂停命令
        commandCenter.playCommand.enabled = !isBuffering
        commandCenter.pauseCommand.enabled = !isBuffering
        commandCenter.togglePlayPauseCommand.enabled = !isBuffering
    }

    /**
     * 隐藏通知
     */
    fun hideNotification() {
        println("🔔 iOS MediaNotificationManager: 隐藏通知")
        nowPlayingInfoCenter.nowPlayingInfo = null
    }

    /**
     * 加载封面图片
     * TODO: 实现异步封面加载
     */
    // private fun loadArtwork(imageUrl: String, completion: (MPMediaItemArtwork?) -> Unit) {
    //     if (imageUrl.isBlank()) {
    //         completion(null)
    //         return
    //     }
    //
    //     try {
    //         // 在后台线程加载图片
    //         val url = NSURL(string = imageUrl)
    //         val data = url?.let { platform.Foundation.NSData.dataWithContentsOfURL(it) }
    //
    //         if (data != null) {
    //             val image = platform.UIKit.UIImage.imageWithData(data)
    //             if (image != null) {
    //                 val artwork = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
    //                 println("🖼️ iOS MediaNotificationManager: 封面加载成功")
    //                 completion(artwork)
    //             } else {
    //                 println("🖼️ iOS MediaNotificationManager: 封面加载失败 - 无法创建UIImage")
    //                 completion(null)
    //             }
    //         } else {
    //             println("🖼️ iOS MediaNotificationManager: 封面加载失败 - 无法获取数据")
    //             completion(null)
    //         }
    //     } catch (e: Exception) {
    //         println("❌ iOS MediaNotificationManager: 封面加载失败 - ${e.message}")
    //         completion(null)
    //     }
    // }

    /**
     * 清理资源
     */
    fun release() {
        hideNotification()

        // 禁用所有命令
        commandCenter.playCommand.enabled = false
        commandCenter.pauseCommand.enabled = false
        commandCenter.togglePlayPauseCommand.enabled = false
        commandCenter.skipForwardCommand.enabled = false
        commandCenter.skipBackwardCommand.enabled = false
        commandCenter.stopCommand.enabled = false
    }
}
