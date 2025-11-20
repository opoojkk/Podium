package com.opoojkk.podium.player

import com.opoojkk.podium.audio.RustAudioPlayerJvm
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class JvmRustPodcastPlayer : PodcastPlayer {

    companion object {
        private const val TAG = "JvmRustPodcastPlayer"
    }

    private val rustPlayer = RustAudioPlayerJvm()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(
        PlaybackState(
            episode = null,
            positionMs = 0L,
            isPlaying = false,
            durationMs = null,
            isBuffering = false,
            playbackSpeed = 1.0f
        )
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentEpisode: Episode? = null
    private var playbackSpeed: Float = 1.0f

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        withContext(Dispatchers.IO) {
            try {
                println("$TAG: Playing episode: ${episode.title}")
                currentEpisode = episode

                updateState(episode = episode, isBuffering = true, isPlaying = false)

                // Get audio path (URL or local file)
                val audioPath = getAudioFilePath(episode)
                if (audioPath == null) {
                    println("$TAG: No valid audio path for episode")
                    updateState(episode = null, isBuffering = false, isPlaying = false)
                    return@withContext
                }

                // Load audio file or URL
                if (audioPath.startsWith("http://") || audioPath.startsWith("https://")) {
                    println("$TAG: Loading audio URL: $audioPath")
                    rustPlayer.loadUrl(audioPath)
                } else {
                    println("$TAG: Loading audio file: $audioPath")
                    rustPlayer.loadFile(audioPath)
                }

                // Wait a bit for duration to be available
                delay(100)

                val duration = rustPlayer.getDuration()
                println("$TAG: Audio loaded, duration: $duration ms")

                // Seek to start position if needed
                if (startPositionMs > 0) {
                    println("$TAG: Seeking to start position: $startPositionMs ms")
                    rustPlayer.seek(startPositionMs)
                }

                // Start playback
                rustPlayer.play()

                updateState(
                    episode = episode,
                    positionMs = startPositionMs,
                    durationMs = if (duration > 0) duration else null,
                    isPlaying = true,
                    isBuffering = false
                )

                startPositionUpdates()

                println("$TAG: Playback started successfully")

            } catch (e: Exception) {
                println("$TAG: Failed to start playback: ${e.message}")
                e.printStackTrace()
                updateState(episode = null, isPlaying = false, isBuffering = false)
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.IO) {
            try {
                println("$TAG: Pausing playback")
                rustPlayer.pause()
                stopPositionUpdates()
                updateState(isPlaying = false)
            } catch (e: Exception) {
                println("$TAG: Failed to pause: ${e.message}")
            }
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.IO) {
            try {
                println("$TAG: Resuming playback")
                rustPlayer.play()
                updateState(isPlaying = true)
                startPositionUpdates()
            } catch (e: Exception) {
                println("$TAG: Failed to resume: ${e.message}")
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                println("$TAG: Stopping playback")
                rustPlayer.stop()
                stopPositionUpdates()
                currentEpisode = null
                updateState(
                    episode = null,
                    positionMs = 0L,
                    durationMs = null,
                    isPlaying = false
                )
            } catch (e: Exception) {
                println("$TAG: Failed to stop: ${e.message}")
            }
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.IO) {
            try {
                println("$TAG: Seeking to: $positionMs ms")
                rustPlayer.seek(positionMs)
                updateState(positionMs = positionMs)
            } catch (e: Exception) {
                println("$TAG: Failed to seek: ${e.message}")
            }
        }
    }

    override suspend fun seekBy(deltaMs: Long) {
        val currentPosition = _state.value.positionMs
        val newPosition = (currentPosition + deltaMs).coerceAtLeast(0)
        seekTo(newPosition)
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        updateState(playbackSpeed = speed)
        println("$TAG: Playback speed set to: $speed (note: not implemented in Rust player yet)")
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val position = rustPlayer.getPosition()
                    val duration = rustPlayer.getDuration()

                    updateState(
                        positionMs = position,
                        durationMs = if (duration > 0) duration else null
                    )

                    // Check if playback has ended
                    if (duration > 0 && position >= duration) {
                        println("$TAG: Playback completed")
                        stop()
                        break
                    }

                    delay(100) // Update every 100ms
                } catch (e: Exception) {
                    println("$TAG: Error updating position: ${e.message}")
                    break
                }
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updateState(
        episode: Episode? = _state.value.episode,
        positionMs: Long = _state.value.positionMs,
        durationMs: Long? = _state.value.durationMs,
        isPlaying: Boolean = _state.value.isPlaying,
        isBuffering: Boolean = _state.value.isBuffering,
        playbackSpeed: Float = this.playbackSpeed
    ) {
        _state.value = PlaybackState(
            episode = episode,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            durationMs = durationMs,
            isBuffering = isBuffering,
            playbackSpeed = playbackSpeed
        )
    }

    /**
     * Get the audio file path or URL for an episode
     * Checks download cache first, returns HTTP URL for streaming if not cached
     */
    private suspend fun getAudioFilePath(episode: Episode): String? {
        // Check if episode is downloaded
        val downloadedFile = getDownloadedFile(episode)
        if (downloadedFile != null && downloadedFile.exists()) {
            println("$TAG: Using downloaded file: ${downloadedFile.absolutePath}")
            return downloadedFile.absolutePath
        }

        // Return HTTP URL for streaming playback
        println("$TAG: Episode not cached, will stream from URL: ${episode.audioUrl}")
        return episode.audioUrl
    }

    /**
     * Get the downloaded file for an episode
     */
    private fun getDownloadedFile(episode: Episode): File? {
        // Try to find the downloaded file in the app's download directory
        // This is platform-specific - adjust path as needed
        val userHome = System.getProperty("user.home")
        val downloadDir = File(userHome, ".podium/downloads")

        if (!downloadDir.exists()) {
            return null
        }

        // Look for file with episode ID
        val possibleFile = File(downloadDir, "${episode.id}.mp3")
        return if (possibleFile.exists()) possibleFile else null
    }

    /**
     * Release resources
     */
    fun release() {
        println("$TAG: Releasing JvmRustPodcastPlayer")
        stopPositionUpdates()
        coroutineScope.cancel()
        rustPlayer.release()
    }
}
