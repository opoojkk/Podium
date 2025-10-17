package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

class DesktopPodcastPlayer : PodcastPlayer {

    private val _state = MutableStateFlow(PlaybackState(null, 0L, false))
    private var clip: Clip? = null
    private var currentEpisode: Episode? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.IO) {
            stop()
            try {
                // Stream directly from URL without downloading entire file
                val audioUrl = URL(episode.audioUrl)
                val baseStream = AudioSystem.getAudioInputStream(audioUrl)
                val baseFormat = baseStream.format
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false,
                )
                val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream)
                val newClip = AudioSystem.getClip().apply {
                    open(decodedStream)
                    if (startPositionMs > 0) {
                        val positionFrames = ((startPositionMs / 1000f) * decodedFormat.frameRate).toInt()
                        framePosition = positionFrames
                    }
                    addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) {
                            _state.value = PlaybackState(null, 0L, false)
                            close()
                        }
                    }
                    start()
                }
                clip = newClip
                currentEpisode = episode
                _state.value = PlaybackState(episode, startPositionMs, true)
            } catch (e: Exception) {
                _state.value = PlaybackState(null, 0L, false)
            }
        }
    }

    override fun pause() {
        clip?.let { player ->
            player.stop()
            _state.value = PlaybackState(currentEpisode, position(), false)
        }
    }

    override fun resume() {
        clip?.let { player ->
            player.start()
            _state.value = PlaybackState(currentEpisode, position(), true)
        }
    }

    override fun stop() {
        clip?.let { player ->
            player.stop()
            player.close()
        }
        clip = null
        currentEpisode = null
        _state.value = PlaybackState(null, 0L, false)
    }

    private fun position(): Long = clip?.let { player ->
        val format = player.format
        if (format != null && format.frameRate > 0) {
            (player.framePosition / format.frameRate * 1000).toLong()
        } else {
            0L
        }
    } ?: 0L
}
