package com.opoojkk.podium.player.android

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidPodcastPlayer(private val context: Context) : PodcastPlayer {

    private val _state = MutableStateFlow(PlaybackState(episode = null, positionMs = 0L, isPlaying = false, durationMs = null, isBuffering = false))
    private var mediaPlayer: MediaPlayer? = null
    private var currentEpisode: Episode? = null
    private var positionUpdateJob: Job? = null
    private var notificationManager: MediaNotificationManager? = null
    private var wasPlayingBeforeSeek = false

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        // 初始化通知管理器
        notificationManager = MediaNotificationManager(
            context = context,
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
        // 设置静态监听器以便BroadcastReceiver使用
        MediaActionReceiver.listener = notificationManager
    }

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.Main) {
            try {
                // Validate audioUrl before attempting to parse
                if (episode.audioUrl.isBlank()) {
                    _state.value = PlaybackState(null, 0L, false)
                    return@withContext
                }
                
                releasePlayer()
				mediaPlayer = MediaPlayer().apply {
                    // 对于HTTP/HTTPS URL，直接使用URL字符串
                    setDataSource(episode.audioUrl)
                    setOnPreparedListener { player ->
                        player.seekTo(startPositionMs.toInt())
                        player.start()
						val newState = PlaybackState(
                            episode = episode,
                            positionMs = player.currentPosition.toLong(),
                            isPlaying = true,
							durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 },
							isBuffering = false,
                        )
                        _state.value = newState
                        updateNotification(newState)
                        startPositionUpdates()
                    }
					setOnInfoListener { mp, what, extra ->
						when (what) {
							MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
								val newState = PlaybackState(
									episode = currentEpisode,
									positionMs = mp.currentPosition.toLong(),
									isPlaying = false,
									durationMs = runCatching { mp.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
									isBuffering = true,
								)
								_state.value = newState
								updateNotification(newState)
							}
							MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
								val newState = PlaybackState(
									episode = currentEpisode,
									positionMs = mp.currentPosition.toLong(),
									isPlaying = mp.isPlaying,
									durationMs = runCatching { mp.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
									isBuffering = false,
								)
								_state.value = newState
								updateNotification(newState)
							}
						}
						true
					}
					setOnSeekCompleteListener { mp ->
						val newState = PlaybackState(
							episode = currentEpisode,
							positionMs = mp.currentPosition.toLong(),
							isPlaying = wasPlayingBeforeSeek && mp.isPlaying,
							durationMs = runCatching { mp.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
							isBuffering = false,
						)
						_state.value = newState
						updateNotification(newState)

						// 如果 seek 之前在播放，恢复播放
						if (wasPlayingBeforeSeek && !mp.isPlaying) {
							mp.start()
							startPositionUpdates()
						}
					}
                    setOnCompletionListener {
                        stopPositionUpdates()
						_state.value = PlaybackState(null, 0L, false, null, false)
                        releasePlayer()
                    }
                    setOnErrorListener { _, what, extra ->
                        stopPositionUpdates()
						_state.value = PlaybackState(null, 0L, false, null, false)
                        releasePlayer()
                        true
                    }
                    prepareAsync()
                }
                currentEpisode = episode
				val initialState = PlaybackState(episode, startPositionMs, false, episode.duration, true)
				_state.value = initialState
				updateNotification(initialState)
            } catch (e: Exception) {
                // Handle any errors during MediaPlayer setup or URI parsing
                stopPositionUpdates()
				_state.value = PlaybackState(null, 0L, false, null, false)
                releasePlayer()
            }
        }
    }

	override fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                stopPositionUpdates()
				val newState = PlaybackState(
                    episode = currentEpisode,
                    positionMs = player.currentPosition.toLong(),
                    isPlaying = false,
					durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
					isBuffering = false,
                )
                _state.value = newState
                updateNotification(newState)
            }
        }
    }

	override fun resume() {
        // 如果播放器未初始化（刚恢复状态），需要先初始化
        if (mediaPlayer == null && currentEpisode != null) {
            val episode = currentEpisode!!
            val startPos = _state.value.positionMs
            println("🎵 Android Player: MediaPlayer not initialized, starting playback from ${startPos}ms")
            CoroutineScope(Dispatchers.Main).launch {
                play(episode, startPos)
            }
        } else {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    startPositionUpdates()
                    val newState = PlaybackState(
                        episode = currentEpisode,
                        positionMs = player.currentPosition.toLong(),
                        isPlaying = true,
                        durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
                        isBuffering = false,
                    )
                    _state.value = newState
                    updateNotification(newState)
                }
            }
        }
    }

    override fun stop() {
        stopPositionUpdates()
        mediaPlayer?.let { player ->
            player.stop()
        }
        releasePlayer()
        _state.value = PlaybackState(null, 0L, false, null)
        notificationManager?.hideNotification()
    }

	override fun seekTo(positionMs: Long) {
		mediaPlayer?.let { player ->
			val duration = runCatching { player.duration.toLong() }.getOrNull() ?: currentEpisode?.duration
			val clamped = duration?.let { positionMs.coerceIn(0L, it) } ?: positionMs.coerceAtLeast(0L)

			// 记录 seek 之前的播放状态
			wasPlayingBeforeSeek = player.isPlaying

			// 设置缓冲状态
			val bufferingState = PlaybackState(
				episode = currentEpisode,
				positionMs = clamped,
				isPlaying = false,
				durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
				isBuffering = true,
			)
			_state.value = bufferingState
			updateNotification(bufferingState)

			// 执行 seek
			player.seekTo(clamped.toInt())

			// seek 完成后会通过 onSeekComplete 监听器更新状态
		}
	}

	override fun seekBy(deltaMs: Long) {
		mediaPlayer?.let { player ->
			val current = player.currentPosition.toLong()
			val duration = runCatching { player.duration.toLong() }.getOrNull() ?: currentEpisode?.duration
			val target = (current + deltaMs)
			val clamped = duration?.let { target.coerceIn(0L, it) } ?: target.coerceAtLeast(0L)
			seekTo(clamped)
		}
	}

	override fun restorePlaybackState(episode: Episode, positionMs: Long) {
		currentEpisode = episode
		_state.value = PlaybackState(
			episode = episode,
			positionMs = positionMs,
			isPlaying = false,
			durationMs = episode.duration,
			isBuffering = false,
		)
	}

    private fun releasePlayer() {
        stopPositionUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        currentEpisode = null
    }

	private fun startPositionUpdates() {
        stopPositionUpdates() // Stop any existing updates
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            var updateCount = 0
            while (isActive && mediaPlayer?.isPlaying == true) {
                val player = mediaPlayer
                val currentPosition = player?.currentPosition?.toLong() ?: 0L
                val duration = player?.let { runCatching { it.duration.toLong() }.getOrNull() }?.takeIf { it > 0 }
				val newState = PlaybackState(
                    episode = currentEpisode,
                    positionMs = currentPosition,
                    isPlaying = true,
					durationMs = duration ?: currentEpisode?.duration,
					isBuffering = false,
                )
                _state.value = newState

                // 每5秒更新一次通知（减少资源消耗）
                updateCount++
                if (updateCount % 5 == 0) {
                    updateNotification(newState)
                }

                delay(1000) // Update every second
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * 更新媒体通知
     */
    private fun updateNotification(state: PlaybackState) {
        println("🎵 AndroidPodcastPlayer: updateNotification called - episode=${state.episode?.title}, isPlaying=${state.isPlaying}, isBuffering=${state.isBuffering}")
        state.episode?.let { episode ->
            notificationManager?.showNotification(
                episode = episode,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isBuffering = state.isBuffering
            )
        } ?: run {
            println("🎵 AndroidPodcastPlayer: 没有正在播放的节目，隐藏通知")
            notificationManager?.hideNotification()
        }
    }
}
