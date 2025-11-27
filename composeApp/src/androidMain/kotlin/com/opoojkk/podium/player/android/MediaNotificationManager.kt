package com.opoojkk.podium.player.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.opoojkk.podium.data.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * 管理播放音频时的媒体通知栏
 */
class MediaNotificationManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onSeekBackward: () -> Unit,
    private val onStop: () -> Unit
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var artworkJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "podium_playback"
        const val CHANNEL_NAME = "播客播放"

        // Actions for notification buttons
        const val ACTION_PLAY_PAUSE = "com.opoojkk.podium.PLAY_PAUSE"
        const val ACTION_SEEK_FORWARD = "com.opoojkk.podium.SEEK_FORWARD"
        const val ACTION_SEEK_BACKWARD = "com.opoojkk.podium.SEEK_BACKWARD"
        const val ACTION_STOP = "com.opoojkk.podium.STOP"

        // Intent extra for opening player detail
        const val EXTRA_SHOW_PLAYER_DETAIL = "show_player_detail"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示正在播放的播客节目，包括播放控制和进度信息"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 禁用通知声音和震动
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
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
        println("🔔 MediaNotificationManager: 尝试显示通知 - ${episode.title}, isPlaying=$isPlaying, isBuffering=$isBuffering")

        artworkJob?.cancel()
        artworkJob = CoroutineScope(Dispatchers.IO).launch {
            val artwork = loadArtwork(episode.imageUrl ?: episode.podcastTitle)
            println("🖼️ MediaNotificationManager: 封面加载${if (artwork != null) "成功" else "失败"}")

            withContext(Dispatchers.Main) {
                try {
                    val notification = buildNotification(episode, isPlaying, positionMs, durationMs, artwork, isBuffering)
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    println("✅ MediaNotificationManager: 通知已发送")
                } catch (e: Exception) {
                    println("❌ MediaNotificationManager: 通知发送失败 - ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 隐藏通知
     */
    fun hideNotification() {
        artworkJob?.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(
        episode: Episode,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long?,
        artwork: Bitmap?,
        isBuffering: Boolean
    ): Notification {
        // 创建打开应用的Intent，并添加标记以显示播放详情页
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_PLAYER_DETAIL, true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建控制按钮的PendingIntent
        val playPauseIntent = createActionIntent(ACTION_PLAY_PAUSE)
        val seekForwardIntent = createActionIntent(ACTION_SEEK_FORWARD)
        val seekBackwardIntent = createActionIntent(ACTION_SEEK_BACKWARD)
        val stopIntent = createActionIntent(ACTION_STOP)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.resources.getIdentifier("ic_notification_small", "drawable", context.packageName))
            .setContentTitle(episode.title)
            .setContentText(episode.podcastTitle)
            .setSubText("Podium")
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            // 设置通知颜色为Material主题色
            .setColorized(false)

        // 如果有封面图，设置为large icon（展开状态显示在左侧）
        artwork?.let { builder.setLargeIcon(it) }

        // 添加进度条（不确定模式用于加载状态）
        if (isBuffering) {
            builder.setProgress(100, 0, true)  // 不确定进度
        } else if (durationMs != null && durationMs > 0) {
            val progress = (positionMs * 100 / durationMs).toInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        }

        // 添加媒体控制按钮
        // 确定播放/暂停按钮的图标和文本
        val playPauseIcon: Int
        val playPauseText: String

        when {
            isBuffering -> {
                // 加载状态：显示加载图标
                playPauseIcon = context.resources.getIdentifier("ic_notification_loading", "drawable", context.packageName)
                playPauseText = "加载中"
            }
            isPlaying -> {
                playPauseIcon = context.resources.getIdentifier("ic_notification_pause", "drawable", context.packageName)
                playPauseText = "暂停"
            }
            else -> {
                playPauseIcon = context.resources.getIdentifier("ic_notification_play", "drawable", context.packageName)
                playPauseText = "播放"
            }
        }

        val rewindIcon = context.resources.getIdentifier("ic_notification_rewind", "drawable", context.packageName)
        val forwardIcon = context.resources.getIdentifier("ic_notification_forward", "drawable", context.packageName)

        builder
            .addAction(
                rewindIcon,
                "快退 15 秒",
                seekBackwardIntent
            )
            .addAction(
                playPauseIcon,
                playPauseText,
                playPauseIntent
            )
            .addAction(
                forwardIcon,
                "快进 30 秒",
                seekForwardIntent
            )

        // 设置媒体样式
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2) // 显示前三个按钮

        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 加载封面图片
     */
    private suspend fun loadArtwork(imageUrl: String?): Bitmap? {
        if (imageUrl.isNullOrBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                val inputStream = connection.getInputStream()
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 处理通知栏的操作按钮点击
     */
    fun handleNotificationAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> onPlayPause()
            ACTION_SEEK_FORWARD -> onSeekForward()
            ACTION_SEEK_BACKWARD -> onSeekBackward()
            ACTION_STOP -> onStop()
        }
    }
}
