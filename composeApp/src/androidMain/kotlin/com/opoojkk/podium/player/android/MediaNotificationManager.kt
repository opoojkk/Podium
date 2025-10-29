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
                description = "显示正在播放的播客节目"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
        durationMs: Long?
    ) {
        println("🔔 MediaNotificationManager: 尝试显示通知 - ${episode.title}, isPlaying=$isPlaying")

        artworkJob?.cancel()
        artworkJob = CoroutineScope(Dispatchers.IO).launch {
            val artwork = loadArtwork(episode.imageUrl ?: episode.podcastTitle)
            println("🖼️ MediaNotificationManager: 封面加载${if (artwork != null) "成功" else "失败"}")

            withContext(Dispatchers.Main) {
                try {
                    val notification = buildNotification(episode, isPlaying, positionMs, durationMs, artwork)
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
        artwork: Bitmap?
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
            .setSmallIcon(android.R.drawable.ic_media_play) // 使用系统默认图标，你可以替换为自己的
            .setContentTitle(episode.title)
            .setContentText(episode.podcastTitle)
            .setSubText("播客")
            .setLargeIcon(artwork)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        // 添加进度条
        if (durationMs != null && durationMs > 0) {
            val progress = (positionMs * 100 / durationMs).toInt()
            builder.setProgress(100, progress, false)
        }

        // 添加媒体控制按钮
        builder
            .addAction(
                android.R.drawable.ic_media_rew,
                "快退",
                seekBackwardIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "暂停" else "播放",
                playPauseIntent
            )
            .addAction(
                android.R.drawable.ic_media_ff,
                "快进",
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
