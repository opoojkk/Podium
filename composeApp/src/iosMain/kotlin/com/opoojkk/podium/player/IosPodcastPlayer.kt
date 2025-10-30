package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.player.ios.MediaNotificationManager
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IosPodcastPlayer : PodcastPlayer {

    private val player = AVPlayer()
    private val _state = MutableStateFlow(PlaybackState(null, 0L, false, null, false, 1.0f))
    private var currentEpisode: Episode? = null
    private var positionUpdateJob: Job? = null
    private var notificationManager: MediaNotificationManager? = null
    private var currentPlaybackSpeed: Float = 1.0f

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        // 初始化通知管理器
        notificationManager = MediaNotificationManager(
            onPlayPause = {
                if (_state.value.isPlaying) {
                    pause()
                } else {
                    resume()
                }
            },
            onSeekForward = {
                seekBy(15000) // 快进15秒
            },
            onSeekBackward = {
                seekBy(-15000) // 快退15秒
            },
            onStop = {
                stop()
            }
        )
    }

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.Main) {
            try {
                // Validate audioUrl before attempting to create NSURL
                if (episode.audioUrl.isBlank()) {
                    _state.value = PlaybackState(null, 0L, false, null, playbackSpeed = currentPlaybackSpeed)
                    return@withContext
                }

                currentEpisode = episode
                val url = createUrlForPlayback(episode.audioUrl)
                if (url == null) {
                    _state.value = PlaybackState(null, 0L, false, null, playbackSpeed = currentPlaybackSpeed)
                    return@withContext
                }

                val item = AVPlayerItem.playerItemWithURL(url)
                player.replaceCurrentItemWithPlayerItem(item)
                val seekTime = CMTimeMakeWithSeconds(startPositionMs.toDouble() / 1000.0, 1000)
                // Emit buffering state while preparing/seek
                val bufferingState = PlaybackState(episode, startPositionMs, false, duration(), true, currentPlaybackSpeed)
                _state.value = bufferingState
                updateNotification(bufferingState)
                player.seekToTime(seekTime) { _ ->
                    // 应用播放速度
                    player.rate = currentPlaybackSpeed
                    val playingState = PlaybackState(episode, startPositionMs, true, duration(), false, currentPlaybackSpeed)
                    _state.value = playingState
                    updateNotification(playingState)
                    startPositionUpdates()
                }
                _state.value = bufferingState
            } catch (e: Exception) {
                // Handle any errors during AVPlayer setup or URL creation
                stopPositionUpdates()
                _state.value = PlaybackState(null, 0L, false, null, false, currentPlaybackSpeed)
                currentEpisode = null
            }
        }
    }

    override fun pause() {
        player.pause()
        stopPositionUpdates()
        val newState = PlaybackState(currentEpisode, currentPosition(), false, duration(), false, currentPlaybackSpeed)
        _state.value = newState
        updateNotification(newState)
    }

    override fun resume() {
        // 如果播放器未初始化内容（刚恢复状态），需要先加载
        if (player.currentItem == null && currentEpisode != null) {
            val episode = currentEpisode!!
            val startPos = _state.value.positionMs
            println("🎵 iOS Player: Player not initialized, starting playback from ${startPos}ms")
            CoroutineScope(Dispatchers.Main).launch {
                play(episode, startPos)
            }
        } else {
            // 使用当前播放速度恢复播放
            player.rate = currentPlaybackSpeed
            startPositionUpdates()
            val newState = PlaybackState(currentEpisode, currentPosition(), true, duration(), false, currentPlaybackSpeed)
            _state.value = newState
            updateNotification(newState)
        }
    }

    override fun stop() {
        stopPositionUpdates()
        player.pause()
        player.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
        currentEpisode = null
        _state.value = PlaybackState(null, 0L, false, null, false, currentPlaybackSpeed)
        notificationManager?.hideNotification()
    }

    override fun seekTo(positionMs: Long) {
        val duration = duration()
        val clamped = duration?.let { positionMs.coerceIn(0L, it) } ?: positionMs.coerceAtLeast(0L)
        val seekTime = CMTimeMakeWithSeconds(clamped.toDouble() / 1000.0, 1000)
        player.seekToTime(seekTime) { _ ->
            val isPlayingNow = player.timeControlStatus == AVPlayerTimeControlStatusPlaying && player.rate > 0.0
            _state.value = PlaybackState(currentEpisode, clamped, isPlayingNow, duration(), false, currentPlaybackSpeed)
        }
    }

    override fun seekBy(deltaMs: Long) {
        val current = currentPosition()
        seekTo(current + deltaMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed = speed.coerceIn(0.5f, 2.0f)

        // 设置 AVPlayer 的播放速率
        player.rate = if (player.timeControlStatus == AVPlayerTimeControlStatusPlaying) {
            currentPlaybackSpeed
        } else {
            0.0f
        }

        // 更新状态
        val newState = _state.value.copy(playbackSpeed = currentPlaybackSpeed)
        _state.value = newState
        updateNotification(newState)

        println("🎵 iOS Player: Playback speed set to ${currentPlaybackSpeed}x")
    }

    override fun restorePlaybackState(episode: Episode, positionMs: Long) {
        currentEpisode = episode
        _state.value = PlaybackState(
            episode = episode,
            positionMs = positionMs,
            isPlaying = false,
            durationMs = episode.duration,
            isBuffering = false,
            playbackSpeed = currentPlaybackSpeed,
        )
    }

    private fun currentPosition(): Long {
        val time = player.currentTime()
        return time.useContents {
            if (timescale != 0) {
                (value * 1000L) / timescale
            } else {
                0L
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates() // Stop any existing updates
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            var updateCount = 0
            while (isActive) {
                val currentPosition = currentPosition()
                val timeControl = player.timeControlStatus
                val isBuffering = (timeControl == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate)
                val isPlayingNow = (timeControl == AVPlayerTimeControlStatusPlaying) && player.rate > 0.0
                val newState = PlaybackState(currentEpisode, currentPosition, isPlayingNow, duration(), isBuffering, currentPlaybackSpeed)
                _state.value = newState

                // 每5秒更新一次通知（减少资源消耗）
                updateCount++
                if (updateCount % 10 == 0) {  // 500ms * 10 = 5秒
                    updateNotification(newState)
                }

                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun duration(): Long? {
        val currentItem = player.currentItem
        val time = currentItem?.duration
        return time?.useContents {
            if (timescale != 0) {
                val ms = (value * 1000L) / timescale
                if (ms > 0) ms else null
            } else {
                null
            }
        }
    }

    private fun createUrlForPlayback(location: String): NSURL? {
        return when {
            location.startsWith("file://") -> {
                val path = location.removePrefix("file://")
                NSURL.fileURLWithPath(path)
            }
            location.contains("://") -> NSURL(string = location)
            else -> NSURL.fileURLWithPath(location)
        }
    }

    /**
     * 更新媒体通知
     */
    private fun updateNotification(state: PlaybackState) {
        println("🎵 iOS PodcastPlayer: updateNotification called - episode=${state.episode?.title}, isPlaying=${state.isPlaying}, isBuffering=${state.isBuffering}")
        state.episode?.let { episode ->
            notificationManager?.showNotification(
                episode = episode,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isBuffering = state.isBuffering
            )
        } ?: run {
            println("🎵 iOS PodcastPlayer: 没有正在播放的节目，隐藏通知")
            notificationManager?.hideNotification()
        }
    }

    /**
     * 清理资源
     */
    fun release() {
        stop()
        notificationManager?.release()
    }
}
