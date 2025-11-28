package com.opoojkk.podium.player.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidPodcastPlayer(private val context: Context) : PodcastPlayer {

    private val _state = MutableStateFlow(PlaybackState(episode = null, positionMs = 0L, isPlaying = false, durationMs = null, isBuffering = false, playbackSpeed = 1.0f))
    private var mediaPlayer: MediaPlayer? = null
    private var currentEpisode: Episode? = null
    private var positionUpdateJob: Job? = null
    private var notificationManager: MediaNotificationManager? = null
    private var wasPlayingBeforeSeek = false
    private var currentPlaybackSpeed: Float = 1.0f
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var isDucked = false
    private val playbackAudioAttributes: AudioAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        } else {
            null
        }
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> handleAudioFocusGain()
            AudioManager.AUDIOFOCUS_LOSS -> handleAudioFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> handleAudioFocusLossTransient()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> handleAudioFocusDuck()
        }
    }

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
                    _state.value = PlaybackState(null, 0L, false, playbackSpeed = currentPlaybackSpeed)
                    return@withContext
                }
                
                releasePlayer()
                if (!requestAudioFocus()) {
                    println("❌ Android Player: Failed to gain audio focus, aborting playback")
                    _state.value = PlaybackState(null, 0L, false, playbackSpeed = currentPlaybackSpeed)
                    return@withContext
                }
				mediaPlayer = MediaPlayer().apply {
                    configureAudioOutput()
                    // 对于HTTP/HTTPS URL，直接使用URL字符串
                    setDataSource(episode.audioUrl)
                    setOnPreparedListener { player ->
                        // 应用播放速度（Android M 及以上）
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            try {
                                player.playbackParams = player.playbackParams.setSpeed(currentPlaybackSpeed)
                            } catch (e: Exception) {
                                println("❌ Android Player: Failed to set playback speed on prepared: ${e.message}")
                            }
                        }

                        // 如果需要seek，先seek再开始播放，并保持缓冲状态
                        // onSeekCompleteListener 会在 seek 完成后更新状态
                        if (startPositionMs > 0) {
                            wasPlayingBeforeSeek = true  // 标记准备播放
                            player.seekTo(startPositionMs.toInt())
                            player.start()
                            resumeOnFocusGain = false
                            // 保持 isBuffering = true，等待 seek 完成
                            val newState = PlaybackState(
                                episode = episode,
                                positionMs = startPositionMs,
                                isPlaying = true,
                                durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 },
                                isBuffering = true,  // 保持缓冲状态直到 seek 完成
                                playbackSpeed = currentPlaybackSpeed,
                            )
                            _state.value = newState
                            updateNotification(newState)
                        } else {
                            // 从头开始播放，不需要 seek
                            player.start()
                            resumeOnFocusGain = false
                            val newState = PlaybackState(
                                episode = episode,
                                positionMs = 0L,
                                isPlaying = true,
                                durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 },
                                isBuffering = false,
                                playbackSpeed = currentPlaybackSpeed,
                            )
                            _state.value = newState
                            updateNotification(newState)
                            startPositionUpdates()
                        }
                    }
					setOnInfoListener { mp, what, extra ->
						when (what) {
							MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
								val newState = PlaybackState(
									episode = currentEpisode,
									positionMs = mp.currentPosition.toLong(),
									isPlaying = mp.isPlaying,  // 保持实际播放状态，不要强制设为false
									durationMs = runCatching { mp.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
									isBuffering = true,
									playbackSpeed = currentPlaybackSpeed,
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
									playbackSpeed = currentPlaybackSpeed,
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
							playbackSpeed = currentPlaybackSpeed,
						)
						_state.value = newState
						updateNotification(newState)

						// 如果 seek 之前在播放，确保播放和位置更新都已启动
						if (wasPlayingBeforeSeek) {
							if (!mp.isPlaying) {
								mp.start()
							}
							startPositionUpdates()
							wasPlayingBeforeSeek = false  // 重置标志
						}
					}
                    setOnCompletionListener {
                        stopPositionUpdates()
						_state.value = PlaybackState(null, 0L, false, null, false, currentPlaybackSpeed)
                        updateNotification(_state.value)
                        releasePlayer(abandonFocus = true)
                    }
                    setOnErrorListener { _, what, extra ->
                        stopPositionUpdates()
						_state.value = PlaybackState(null, 0L, false, null, false, currentPlaybackSpeed)
                        updateNotification(_state.value)
                        releasePlayer(abandonFocus = true)
                        true
                    }
                    prepareAsync()
                }
                currentEpisode = episode
				val initialState = PlaybackState(episode, startPositionMs, false, episode.duration, true, currentPlaybackSpeed)
				_state.value = initialState
				updateNotification(initialState)
            } catch (e: Exception) {
                // Handle any errors during MediaPlayer setup or URI parsing
                stopPositionUpdates()
				_state.value = PlaybackState(null, 0L, false, null, false, currentPlaybackSpeed)
                updateNotification(_state.value)
                releasePlayer(abandonFocus = true)
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
					playbackSpeed = currentPlaybackSpeed,
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
            com.opoojkk.podium.util.Logger.d("AndroidPodcastPlayer") { "🎵 MediaPlayer not initialized, re-initializing from ${startPos}ms" }

            // 立即更新状态为缓冲中，给用户即时反馈
            _state.value = PlaybackState(
                episode = episode,
                positionMs = startPos,
                isPlaying = false,
                durationMs = episode.duration,
                isBuffering = true,
                playbackSpeed = currentPlaybackSpeed,
            )

            // 异步重新初始化播放器
            CoroutineScope(Dispatchers.Main).launch {
                play(episode, startPos)
            }
        } else {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    if (!requestAudioFocus()) {
                        com.opoojkk.podium.util.Logger.w("AndroidPodcastPlayer") { "❌ Failed to regain audio focus on resume" }
                        return@let
                    }
                    player.setVolume(1f, 1f)
                    resumeOnFocusGain = false
                    player.start()
                    startPositionUpdates()
                    val newState = PlaybackState(
                        episode = currentEpisode,
                        positionMs = player.currentPosition.toLong(),
                        isPlaying = true,
                        durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
                        isBuffering = false,
                        playbackSpeed = currentPlaybackSpeed,
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
        val stoppedState = PlaybackState(null, 0L, false, null, playbackSpeed = currentPlaybackSpeed)
        _state.value = stoppedState
        updateNotification(stoppedState)
        releasePlayer(abandonFocus = true)
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
				playbackSpeed = currentPlaybackSpeed,
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

	override fun setPlaybackSpeed(speed: Float) {
		currentPlaybackSpeed = speed.coerceIn(0.5f, 2.0f)
		mediaPlayer?.let { player ->
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
				try {
					player.playbackParams = player.playbackParams.setSpeed(currentPlaybackSpeed)
					val newState = _state.value.copy(playbackSpeed = currentPlaybackSpeed)
					_state.value = newState
					updateNotification(newState)
					println("🎵 Android Player: Playback speed set to ${currentPlaybackSpeed}x")
				} catch (e: Exception) {
					println("❌ Android Player: Failed to set playback speed: ${e.message}")
				}
			} else {
				println("⚠️ Android Player: Playback speed control requires Android M (API 23) or higher")
			}
		}
	}

	override fun restorePlaybackState(episode: Episode, positionMs: Long) {
		com.opoojkk.podium.util.Logger.d("AndroidPodcastPlayer") { "🎵 restorePlaybackState called - episode=${episode.title}, positionMs=${positionMs}" }
		currentEpisode = episode
		val newState = PlaybackState(
			episode = episode,
			positionMs = positionMs,
			isPlaying = false,
			durationMs = episode.duration,
			isBuffering = false,
			playbackSpeed = currentPlaybackSpeed,
		)
		_state.value = newState
		com.opoojkk.podium.util.Logger.d("AndroidPodcastPlayer") { "🎵 State updated successfully - episode=${_state.value.episode?.title}" }
	}

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).apply {
                playbackAudioAttributes?.let { setAudioAttributes(it) }
                setOnAudioFocusChangeListener(audioFocusChangeListener)
                setAcceptsDelayedFocusGain(true)
                setWillPauseWhenDucked(false)
            }.build().also { audioFocusRequest = it }
            manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        audioFocusRequest = null
    }

    private fun handleAudioFocusGain() {
        CoroutineScope(Dispatchers.Main).launch {
            isDucked = false
            val player = mediaPlayer ?: return@launch
            player.setVolume(1f, 1f)
            if (resumeOnFocusGain && !player.isPlaying) {
                resumeOnFocusGain = false
                player.start()
                startPositionUpdates()
                val newState = PlaybackState(
                    episode = currentEpisode,
                    positionMs = player.currentPosition.toLong(),
                    isPlaying = true,
                    durationMs = runCatching { player.duration.toLong() }.getOrNull()?.takeIf { it > 0 } ?: currentEpisode?.duration,
                    isBuffering = false,
                    playbackSpeed = currentPlaybackSpeed,
                )
                _state.value = newState
                updateNotification(newState)
            } else {
                resumeOnFocusGain = false
            }
        }
    }

    private fun handleAudioFocusLoss() {
        CoroutineScope(Dispatchers.Main).launch {
            resumeOnFocusGain = false
            stop()
        }
    }

    private fun handleAudioFocusLossTransient() {
        CoroutineScope(Dispatchers.Main).launch {
            val player = mediaPlayer
            if (player?.isPlaying == true) {
                resumeOnFocusGain = true
                pause()
            }
        }
    }

    private fun handleAudioFocusDuck() {
        CoroutineScope(Dispatchers.Main).launch {
            val player = mediaPlayer
            if (player?.isPlaying == true) {
                isDucked = true
                player.setVolume(0.3f, 0.3f)
            }
        }
    }

    private fun MediaPlayer.configureAudioOutput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            playbackAudioAttributes?.let { setAudioAttributes(it) }
        } else {
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
        setVolume(1f, 1f)
    }

    private fun releasePlayer(abandonFocus: Boolean = false) {
        stopPositionUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        currentEpisode = null
        wasPlayingBeforeSeek = false
        isDucked = false
        resumeOnFocusGain = false
        if (abandonFocus) {
            abandonAudioFocus()
            notificationManager?.hideNotification()
        }
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
					playbackSpeed = currentPlaybackSpeed,
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
