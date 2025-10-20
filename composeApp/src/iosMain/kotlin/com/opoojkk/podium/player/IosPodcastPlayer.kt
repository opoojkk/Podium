package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.kCMTimeZero
import platform.Foundation.NSURL
import platform.CoreMedia.CMTimeMakeWithSeconds

class IosPodcastPlayer : PodcastPlayer {

    private val player = AVPlayer()
    private val _state = MutableStateFlow(PlaybackState(null, 0L, false))
    private var currentEpisode: Episode? = null
    private var positionUpdateJob: Job? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.Main) {
            try {
                // Validate audioUrl before attempting to create NSURL
                if (episode.audioUrl.isBlank()) {
                    _state.value = PlaybackState(null, 0L, false)
                    return@withContext
                }
                
                currentEpisode = episode
                val url = NSURL(string = episode.audioUrl)
                
                // Check if URL creation was successful
                if (url == null) {
                    _state.value = PlaybackState(null, 0L, false)
                    return@withContext
                }
                
                val item = AVPlayerItem.playerItemWithURL(url)
                player.replaceCurrentItemWithPlayerItem(item)
                val seekTime = CMTimeMakeWithSeconds(startPositionMs.toDouble() / 1000.0, 1000)
                player.seekToTime(seekTime) { _ ->
                    player.play()
                    _state.value = PlaybackState(episode, startPositionMs, true)
                    startPositionUpdates()
                }
                _state.value = PlaybackState(episode, startPositionMs, false)
            } catch (e: Exception) {
                // Handle any errors during AVPlayer setup or URL creation
                stopPositionUpdates()
                _state.value = PlaybackState(null, 0L, false)
                currentEpisode = null
            }
        }
    }

    override fun pause() {
        player.pause()
        stopPositionUpdates()
        _state.value = PlaybackState(currentEpisode, currentPosition(), false)
    }

    override fun resume() {
        player.play()
        startPositionUpdates()
        _state.value = PlaybackState(currentEpisode, currentPosition(), true)
    }

    override fun stop() {
        stopPositionUpdates()
        player.pause()
        player.seekToTime(kCMTimeZero)
        currentEpisode = null
        _state.value = PlaybackState(null, 0L, false)
    }

    private fun currentPosition(): Long {
        val time = player.currentTime()
        return if (time.timescale != 0L) {
            (time.value * 1000L) / time.timescale
        } else {
            0L
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates() // Stop any existing updates
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && player.rate > 0.0) {
                val currentPosition = currentPosition()
                _state.value = PlaybackState(currentEpisode, currentPosition, true)
                delay(1000) // Update every second
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
}
