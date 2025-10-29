package com.opoojkk.podium.player.jvm

import com.opoojkk.podium.data.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/**
 * 管理播放音频时的媒体通知栏(JVM/Desktop)
 * 使用系统托盘显示播放状态
 */
class MediaNotificationManager(
    private val onPlayPause: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onSeekBackward: () -> Unit,
    private val onStop: () -> Unit
) {
    private var trayIcon: TrayIcon? = null
    private var artworkJob: Job? = null
    private var currentImage: Image? = null

    companion object {
        // Intent extra for opening player detail
        const val EXTRA_SHOW_PLAYER_DETAIL = "show_player_detail"

        // 默认图标尺寸
        private const val ICON_SIZE = 16
    }

    init {
        setupSystemTray()
    }

    /**
     * 设置系统托盘
     */
    private fun setupSystemTray() {
        if (!SystemTray.isSupported()) {
            println("⚠️ JVM MediaNotificationManager: 系统不支持托盘图标")
            return
        }

        try {
            val systemTray = SystemTray.getSystemTray()

            // 创建一个简单的默认图标
            val defaultImage = createDefaultIcon()
            currentImage = defaultImage

            // 创建托盘图标
            trayIcon = TrayIcon(defaultImage, "Podium").apply {
                isImageAutoSize = true

                // 添加鼠标点击监听器
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) {
                            // 单击打开播放器详情
                            println("🔔 JVM MediaNotificationManager: Tray icon clicked")
                        }
                    }
                })

                // 创建弹出菜单
                val popup = java.awt.PopupMenu()

                // 播放/暂停
                val playPauseItem = java.awt.MenuItem("播放/暂停").apply {
                    addActionListener {
                        println("🔔 JVM MediaNotificationManager: Play/Pause clicked")
                        onPlayPause()
                    }
                }
                popup.add(playPauseItem)

                popup.addSeparator()

                // 快退
                val seekBackwardItem = java.awt.MenuItem("快退 15秒").apply {
                    addActionListener {
                        println("🔔 JVM MediaNotificationManager: Seek backward clicked")
                        onSeekBackward()
                    }
                }
                popup.add(seekBackwardItem)

                // 快进
                val seekForwardItem = java.awt.MenuItem("快进 15秒").apply {
                    addActionListener {
                        println("🔔 JVM MediaNotificationManager: Seek forward clicked")
                        onSeekForward()
                    }
                }
                popup.add(seekForwardItem)

                popup.addSeparator()

                // 停止
                val stopItem = java.awt.MenuItem("停止").apply {
                    addActionListener {
                        println("🔔 JVM MediaNotificationManager: Stop clicked")
                        onStop()
                    }
                }
                popup.add(stopItem)

                popupMenu = popup
            }

            // 添加到系统托盘
            systemTray.add(trayIcon)
            println("✅ JVM MediaNotificationManager: 托盘图标已创建")
        } catch (e: Exception) {
            println("❌ JVM MediaNotificationManager: 创建托盘图标失败 - ${e.message}")
            e.printStackTrace()
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
        println("🔔 JVM MediaNotificationManager: 尝试显示通知 - ${episode.title}, isPlaying=$isPlaying, isBuffering=$isBuffering")

        // 更新托盘图标提示文本
        val statusText = when {
            isBuffering -> "加载中"
            isPlaying -> "播放中"
            else -> "已暂停"
        }

        val durationText = if (durationMs != null && durationMs > 0) {
            val positionSec = positionMs / 1000
            val durationSec = durationMs / 1000
            " - ${formatTime(positionSec)}/${formatTime(durationSec)}"
        } else {
            ""
        }

        trayIcon?.toolTip = "Podium - $statusText\n${episode.title}\n${episode.podcastTitle}$durationText"

        // 加载封面图片作为托盘图标
        artworkJob?.cancel()
        artworkJob = CoroutineScope(Dispatchers.IO).launch {
            val artwork = loadArtwork(episode.imageUrl)
            if (artwork != null) {
                currentImage = artwork
                trayIcon?.image = artwork
                println("🖼️ JVM MediaNotificationManager: 封面已更新为托盘图标")
            }
        }

        println("✅ JVM MediaNotificationManager: 通知已更新")
    }

    /**
     * 隐藏通知
     */
    fun hideNotification() {
        println("🔔 JVM MediaNotificationManager: 重置通知")
        artworkJob?.cancel()
        trayIcon?.toolTip = "Podium"

        // 恢复默认图标
        trayIcon?.image = createDefaultIcon()
    }

    /**
     * 加载封面图片
     */
    private fun loadArtwork(imageUrl: String?): Image? {
        if (imageUrl.isNullOrBlank()) return null

        return try {
            val url = URL(imageUrl)
            val image = ImageIO.read(url)
            if (image != null) {
                // 缩放到适合托盘图标的大小
                val traySize = SystemTray.getSystemTray().trayIconSize
                val size = maxOf(traySize.width, traySize.height, ICON_SIZE)
                image.getScaledInstance(size, size, Image.SCALE_SMOOTH)
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ JVM MediaNotificationManager: 封面加载失败 - ${e.message}")
            null
        }
    }

    /**
     * 创建默认图标
     */
    private fun createDefaultIcon(): Image {
        // 创建一个简单的彩色方块作为默认图标
        val size = if (SystemTray.isSupported()) {
            val traySize = SystemTray.getSystemTray().trayIconSize
            maxOf(traySize.width, traySize.height, ICON_SIZE)
        } else {
            ICON_SIZE
        }

        val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        // 绘制一个圆形图标
        g.color = java.awt.Color(0, 122, 255) // iOS蓝色
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4)

        // 绘制一个播放三角形
        g.color = java.awt.Color.WHITE
        val margin = size / 4
        val trianglePoints = intArrayOf(
            margin, margin,
            margin, size - margin,
            size - margin, size / 2
        )
        g.fillPolygon(
            intArrayOf(trianglePoints[0], trianglePoints[2], trianglePoints[4]),
            intArrayOf(trianglePoints[1], trianglePoints[3], trianglePoints[5]),
            3
        )

        g.dispose()
        return image
    }

    /**
     * 格式化时间（秒 -> MM:SS）
     */
    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    /**
     * 清理资源
     */
    fun release() {
        artworkJob?.cancel()

        if (SystemTray.isSupported() && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon)
                println("✅ JVM MediaNotificationManager: 托盘图标已移除")
            } catch (e: Exception) {
                println("❌ JVM MediaNotificationManager: 移除托盘图标失败 - ${e.message}")
            }
        }

        trayIcon = null
    }
}
