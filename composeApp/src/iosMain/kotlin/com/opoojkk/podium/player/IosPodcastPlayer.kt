package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
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
    private val _state = MutableStateFlow(PlaybackState(null, 0L, false, null, false))
    private var currentEpisode: Episode? = null
    private var positionUpdateJob: Job? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.Main) {
            try {
                // Validate audioUrl before attempting to create NSURL
                if (episode.audioUrl.isBlank()) {
                    _state.value = PlaybackState(null, 0L, false, null)
                    return@withContext
                }
                
                currentEpisode = episode
                val url = NSURL(string = episode.audioUrl)
                
                // Check if URL creation was successful
                if (url == null) {
                    _state.value = PlaybackState(null, 0L, false, null)
                    return@withContext
                }
                
                val item = AVPlayerItem.playerItemWithURL(url)
                player.replaceCurrentItemWithPlayerItem(item)
                val seekTime = CMTimeMakeWithSeconds(startPositionMs.toDouble() / 1000.0, 1000)
                // Emit buffering state while preparing/seek
                _state.value = PlaybackState(episode, startPositionMs, false, duration(), true)
                player.seekToTime(seekTime) { _ ->
                    player.play()
                    _state.value = PlaybackState(episode, startPositionMs, true, duration(), false)
                    startPositionUpdates()
                }
                _state.value = PlaybackState(episode, startPositionMs, false, duration(), true)
            } catch (e: Exception) {
                // Handle any errors during AVPlayer setup or URL creation
                stopPositionUpdates()
                _state.value = PlaybackState(null, 0L, false, null, false)
                currentEpisode = null
            }
        }
    }

    override fun pause() {
        player.pause()
        stopPositionUpdates()
        _state.value = PlaybackState(currentEpisode, currentPosition(), false, duration(), false)
    }

    override fun resume() {
        player.play()
        startPositionUpdates()
        _state.value = PlaybackState(currentEpisode, currentPosition(), true, duration(), false)
    }

    override fun stop() {
        stopPositionUpdates()
        player.pause()
        player.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
        currentEpisode = null
        _state.value = PlaybackState(null, 0L, false, null, false)
    }

    override fun seekTo(positionMs: Long) {
        val duration = duration()
        val clamped = duration?.let { positionMs.coerceIn(0L, it) } ?: positionMs.coerceAtLeast(0L)
        val seekTime = CMTimeMakeWithSeconds(clamped.toDouble() / 1000.0, 1000)
        player.seekToTime(seekTime) { _ ->
            val isPlayingNow = player.timeControlStatus == AVPlayerTimeControlStatusPlaying && player.rate > 0.0
            _state.value = PlaybackState(currentEpisode, clamped, isPlayingNow, duration(), false)
        }
    }

    override fun seekBy(deltaMs: Long) {
        val current = currentPosition()
        seekTo(current + deltaMs)
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
            while (isActive) {
                val currentPosition = currentPosition()
                val timeControl = player.timeControlStatus
                val isBuffering = (timeControl == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate)
                val isPlayingNow = (timeControl == AVPlayerTimeControlStatusPlaying) && player.rate > 0.0
                _state.value = PlaybackState(currentEpisode, currentPosition, isPlayingNow, duration(), isBuffering)
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
}
