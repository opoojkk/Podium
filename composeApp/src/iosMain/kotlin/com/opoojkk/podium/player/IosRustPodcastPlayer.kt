package com.opoojkk.podium.player

import com.opoojkk.podium.audio.RustAudioPlayerIos
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.*

/**
 * iOS implementation of PodcastPlayer using Rust audio player
 */
class IosRustPodcastPlayer : PodcastPlayer {

    companion object {
        private const val TAG = "IosRustPodcastPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L // 1 second
    }

    private val rustPlayer = RustAudioPlayerIos()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
    private var targetPlaybackSpeed: Float = 1.0f

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        println("$TAG: play() - episode: ${episode.title}, startPosition: $startPositionMs")

        try {
            // Update state to show buffering
            updateState(
                episode = episode,
                isBuffering = true,
                isPlaying = false
            )

            // Get audio file path (download if needed)
            val audioPath = withContext(Dispatchers.Default) {
                getAudioFilePath(episode)
            }

            if (audioPath == null) {
                println("$TAG: No audio file found for episode: ${episode.title}")
                updateState(
                    episode = null,
                    isBuffering = false,
                    isPlaying = false
                )
                return
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
                rustPlayer.seek(startPositionMs)
            }

            // Start playback
            rustPlayer.play()
            currentEpisode = episode

            // Update state
            updateState(
                episode = episode,
                positionMs = startPositionMs,
                isPlaying = true,
                isBuffering = false,
                durationMs = if (duration > 0) duration else episode.duration
            )

            // Start position updates
            startPositionUpdates()

            println("$TAG: Playback started successfully")

        } catch (e: Exception) {
            println("$TAG: Failed to start playback: ${e.message}")
            updateState(
                episode = null,
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    override fun pause() {
        println("$TAG: pause()")

        // Check if currently playing
        if (!_state.value.isPlaying) {
            println("$TAG: Cannot pause - not currently playing")
            return
        }

        try {
            rustPlayer.pause()
            stopPositionUpdates()

            updateState(
                isPlaying = false
            )

            println("$TAG: Playback paused")
        } catch (e: Exception) {
            println("$TAG: Failed to pause: ${e.message}")
        }
    }

    override fun resume() {
        println("$TAG: resume()")

        // Check if there's an episode loaded
        val episode = currentEpisode
        if (episode == null) {
            println("$TAG: Cannot resume - no episode loaded")
            return
        }

        // Check current state
        val playerState = rustPlayer.getState()
        if (playerState == RustAudioPlayerIos.PlayerState.IDLE) {
            // If player is in Idle state but we have an episode, reload it
            println("$TAG: Player is in Idle state, reloading audio and resuming from position: ${_state.value.positionMs}")

            coroutineScope.launch {
                try {
                    // Reload the episode from the last known position
                    play(episode, _state.value.positionMs)
                } catch (e: Exception) {
                    println("$TAG: Failed to reload and resume playback: ${e.message}")
                }
            }
            return
        }

        try {
            rustPlayer.play()

            updateState(
                isPlaying = true
            )

            startPositionUpdates()

            println("$TAG: Playback resumed")
        } catch (e: Exception) {
            println("$TAG: Failed to resume: ${e.message}")
        }
    }

    override fun stop() {
        println("$TAG: stop()")

        try {
            rustPlayer.stop()
            stopPositionUpdates()
            currentEpisode = null

            _state.value = PlaybackState(
                episode = null,
                positionMs = 0L,
                isPlaying = false,
                durationMs = null,
                isBuffering = false,
                playbackSpeed = 1.0f
            )

            println("$TAG: Playback stopped")
        } catch (e: Exception) {
            println("$TAG: Failed to stop: ${e.message}")
        }
    }

    override fun seekTo(positionMs: Long) {
        println("$TAG: seekTo() - position: $positionMs")

        try {
            rustPlayer.seek(positionMs)

            updateState(
                positionMs = positionMs
            )

            println("$TAG: Seeked to $positionMs ms")
        } catch (e: Exception) {
            println("$TAG: Failed to seek: ${e.message}")
        }
    }

    override fun seekBy(deltaMs: Long) {
        val currentPosition = _state.value.positionMs
        val newPosition = (currentPosition + deltaMs).coerceAtLeast(0L)

        // Don't seek past duration if we know it
        val duration = _state.value.durationMs
        val targetPosition = if (duration != null) {
            newPosition.coerceAtMost(duration)
        } else {
            newPosition
        }

        seekTo(targetPosition)
    }

    override fun setPlaybackSpeed(speed: Float) {
        println("$TAG: setPlaybackSpeed() - speed: $speed")

        // Store target speed for future reference
        targetPlaybackSpeed = speed

        // TODO: Implement playback speed in Rust player
        // For now, we just update the state
        println("$TAG: Playback speed change not yet implemented in Rust player")

        updateState(
            playbackSpeed = speed
        )
    }

    override fun restorePlaybackState(episode: Episode, positionMs: Long) {
        println("$TAG: restorePlaybackState() - episode: ${episode.title}, position: $positionMs")

        currentEpisode = episode

        updateState(
            episode = episode,
            positionMs = positionMs,
            isPlaying = false,
            durationMs = episode.duration,
            playbackSpeed = targetPlaybackSpeed
        )
    }

    /**
     * Start periodic position updates
     */
    private fun startPositionUpdates() {
        stopPositionUpdates()

        positionUpdateJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val position = rustPlayer.getPosition()
                    val duration = rustPlayer.getDuration()

                    updateState(
                        positionMs = position,
                        durationMs = if (duration > 0) duration else _state.value.durationMs
                    )

                    // Check if playback finished
                    if (duration > 0 && position >= duration) {
                        println("$TAG: Playback finished")
                        pause()
                        break
                    }
                } catch (e: Exception) {
                    println("$TAG: Error updating position: ${e.message}")
                }

                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop position updates
     */
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * Update playback state with new values
     */
    private fun updateState(
        episode: Episode? = _state.value.episode,
        positionMs: Long = _state.value.positionMs,
        isPlaying: Boolean = _state.value.isPlaying,
        durationMs: Long? = _state.value.durationMs,
        isBuffering: Boolean = _state.value.isBuffering,
        playbackSpeed: Float = _state.value.playbackSpeed
    ) {
        _state.value = PlaybackState(
            episode = episode,
            positionMs = positionMs,
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
        if (downloadedFile != null && NSFileManager.defaultManager.fileExistsAtPath(downloadedFile)) {
            println("$TAG: Using downloaded file: $downloadedFile")
            return downloadedFile
        }

        // Return HTTP URL for streaming playback
        println("$TAG: Episode not cached, will stream from URL: ${episode.audioUrl}")
        return episode.audioUrl
    }

    /**
     * Get the downloaded file for an episode
     */
    private fun getDownloadedFile(episode: Episode): String? {
        // Get app's documents directory
        val documentsPaths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )

        if (documentsPaths.isEmpty()) {
            return null
        }

        val documentsPath = documentsPaths[0] as String
        val downloadsPath = "$documentsPath/downloads"
        val episodeFile = "$downloadsPath/${episode.id}.mp3"

        if (NSFileManager.defaultManager.fileExistsAtPath(episodeFile)) {
            return episodeFile
        }

        // Check cache directory as fallback
        val cachePaths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )

        if (cachePaths.isNotEmpty()) {
            val cachePath = cachePaths[0] as String
            val cacheFile = "$cachePath/podcasts/${episode.id}.mp3"
            if (NSFileManager.defaultManager.fileExistsAtPath(cacheFile)) {
                return cacheFile
            }
        }

        return null
    }

    /**
     * Clean up resources when player is no longer needed
     */
    fun release() {
        println("$TAG: Releasing IosRustPodcastPlayer")
        stopPositionUpdates()
        coroutineScope.cancel()
        rustPlayer.release()
    }
}
