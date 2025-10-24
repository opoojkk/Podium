package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PodcastPlayer {
    val state: StateFlow<PlaybackState>

    suspend fun play(episode: Episode, startPositionMs: Long = 0L)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
}
