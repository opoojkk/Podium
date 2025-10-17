package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.kCMTimeZero
import platform.Foundation.NSURL

class IosPodcastPlayer : PodcastPlayer {

    private val player = AVPlayer()
    private val _state = MutableStateFlow(PlaybackState(null, 0L, false))
    private var currentEpisode: Episode? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.Main) {
            currentEpisode = episode
            val item = AVPlayerItem.playerItemWithURL(NSURL(string = episode.audioUrl))
            player.replaceCurrentItemWithPlayerItem(item)
            player.play()
            _state.value = PlaybackState(episode, startPositionMs, true)
        }
    }

    override fun pause() {
        player.pause()
        _state.value = PlaybackState(currentEpisode, currentPosition(), false)
    }

    override fun resume() {
        player.play()
        _state.value = PlaybackState(currentEpisode, currentPosition(), true)
    }

    override fun stop() {
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
}
