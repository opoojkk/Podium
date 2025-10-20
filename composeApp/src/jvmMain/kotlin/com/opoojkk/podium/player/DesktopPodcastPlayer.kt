package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.*
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
    private var positionUpdateJob: Job? = null

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        println("🎵 DesktopPodcastPlayer: Starting playback for episode: ${episode.title}")
        println("🎵 DesktopPodcastPlayer: Audio URL: ${episode.audioUrl}")
        withContext(Dispatchers.IO) {
            stop()
            try {
                // Download the audio file first, then play it
                val audioUrl = URL(episode.audioUrl)
                println("🎵 DesktopPodcastPlayer: Downloading audio file...")
                val connection = audioUrl.openConnection()
                connection.setRequestProperty("User-Agent", "Podium/1.0")
                val inputStream = connection.getInputStream()
                val baseStream = AudioSystem.getAudioInputStream(inputStream)
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
                    println("🎵 DesktopPodcastPlayer: Opening audio stream")
                    open(decodedStream)
                    if (startPositionMs > 0) {
                        val positionFrames = ((startPositionMs / 1000f) * decodedFormat.frameRate).toInt()
                        framePosition = positionFrames
                    }
                    addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) {
                            println("🎵 DesktopPodcastPlayer: Playback stopped")
                            _state.value = PlaybackState(null, 0L, false)
                            close()
                        }
                    }
                    println("🎵 DesktopPodcastPlayer: Starting playback")
                    start()
                }
                clip = newClip
                currentEpisode = episode
                _state.value = PlaybackState(episode, startPositionMs, true)
                startPositionUpdates()
                println("🎵 DesktopPodcastPlayer: State updated to playing")
            } catch (e: Exception) {
                println("🎵 DesktopPodcastPlayer: Exception occurred: ${e.message}")
                e.printStackTrace()
                _state.value = PlaybackState(null, 0L, false)
            }
        }
    }

    override fun pause() {
        clip?.let { player ->
            player.stop()
            stopPositionUpdates()
            _state.value = PlaybackState(currentEpisode, position(), false)
        }
    }

    override fun resume() {
        clip?.let { player ->
            player.start()
            startPositionUpdates()
            _state.value = PlaybackState(currentEpisode, position(), true)
        }
    }

    override fun stop() {
        stopPositionUpdates()
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

    private fun startPositionUpdates() {
        stopPositionUpdates() // Stop any existing updates
        positionUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && clip?.isActive == true) {
                val currentPosition = position()
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
