package com.opoojkk.podium.player.android

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidPodcastPlayer(private val context: Context) : PodcastPlayer {

    private val _state = MutableStateFlow(PlaybackState(episode = null, positionMs = 0L, isPlaying = false))
    private var mediaPlayer: MediaPlayer? = null
    private var currentEpisode: Episode? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

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
                    setDataSource(context, Uri.parse(episode.audioUrl))
                    setOnPreparedListener { player ->
                        player.seekTo(startPositionMs.toInt())
                        player.start()
                        _state.value = PlaybackState(episode, player.currentPosition.toLong(), true)
                    }
                    setOnCompletionListener {
                        _state.value = PlaybackState(null, 0L, false)
                        releasePlayer()
                    }
                    setOnErrorListener { _, what, extra ->
                        _state.value = PlaybackState(null, 0L, false)
                        releasePlayer()
                        true
                    }
                    prepareAsync()
                }
                currentEpisode = episode
                _state.value = PlaybackState(episode, startPositionMs, false)
            } catch (e: Exception) {
                // Handle any errors during MediaPlayer setup or URI parsing
                _state.value = PlaybackState(null, 0L, false)
                releasePlayer()
            }
        }
    }

    override fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _state.value = PlaybackState(currentEpisode, player.currentPosition.toLong(), false)
            }
        }
    }

    override fun resume() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                _state.value = PlaybackState(currentEpisode, player.currentPosition.toLong(), true)
            }
        }
    }

    override fun stop() {
        mediaPlayer?.let { player ->
            player.stop()
        }
        releasePlayer()
        _state.value = PlaybackState(null, 0L, false)
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentEpisode = null
    }
}
