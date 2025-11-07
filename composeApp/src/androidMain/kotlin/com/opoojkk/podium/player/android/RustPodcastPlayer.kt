package com.opoojkk.podium.player.android

import android.content.Context
import android.util.Log
import com.opoojkk.podium.audio.RustAudioPlayer
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import com.opoojkk.podium.player.PodcastPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URL

/**
 * Rust-based implementation of PodcastPlayer
 * Uses the Rust audio player library for low-latency, high-performance playback
 */
class RustPodcastPlayer(
    private val context: Context
) : PodcastPlayer {

    companion object {
        private const val TAG = "RustPodcastPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L // 1 second
    }

    private val rustPlayer = RustAudioPlayer()
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
        Log.d(TAG, "play() - episode: ${episode.title}, startPosition: $startPositionMs")

        try {
            // Update state to show buffering
            updateState(
                episode = episode,
                isBuffering = true,
                isPlaying = false
            )

            // Get audio file path (download if needed)
            val audioPath = withContext(Dispatchers.IO) {
                getAudioFilePath(episode)
            }

            if (audioPath == null) {
                Log.e(TAG, "No audio file found for episode: ${episode.title}")
                updateState(
                    episode = null,
                    isBuffering = false,
                    isPlaying = false
                )
                return
            }

            // Load audio file
            Log.d(TAG, "Loading audio file: $audioPath")
            rustPlayer.loadFile(audioPath)

            // Wait a bit for duration to be available
            delay(100)

            val duration = rustPlayer.getDuration()
            Log.d(TAG, "Audio loaded, duration: $duration ms")

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

            Log.d(TAG, "Playback started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            updateState(
                episode = null,
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    override fun pause() {
        Log.d(TAG, "pause()")

        // Check if currently playing
        if (!_state.value.isPlaying) {
            Log.w(TAG, "Cannot pause - not currently playing")
            return
        }

        try {
            rustPlayer.pause()
            stopPositionUpdates()

            updateState(
                isPlaying = false
            )

            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause", e)
        }
    }

    override fun resume() {
        Log.d(TAG, "resume()")

        // Check if there's an episode loaded
        if (currentEpisode == null) {
            Log.w(TAG, "Cannot resume - no episode loaded")
            return
        }

        // Check current state
        val playerState = rustPlayer.getState()
        if (playerState == RustAudioPlayer.PlayerState.IDLE) {
            Log.w(TAG, "Cannot resume - player is in Idle state (no audio loaded)")
            return
        }

        try {
            rustPlayer.play()

            updateState(
                isPlaying = true
            )

            startPositionUpdates()

            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume", e)
        }
    }

    override fun stop() {
        Log.d(TAG, "stop()")

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

            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop", e)
        }
    }

    override fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo() - position: $positionMs")

        try {
            rustPlayer.seek(positionMs)

            updateState(
                positionMs = positionMs
            )

            Log.d(TAG, "Seeked to $positionMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
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
        Log.d(TAG, "setPlaybackSpeed() - speed: $speed")

        // Store target speed for future reference
        targetPlaybackSpeed = speed

        // TODO: Implement playback speed in Rust player
        // For now, we just update the state
        Log.w(TAG, "Playback speed change not yet implemented in Rust player")

        updateState(
            playbackSpeed = speed
        )
    }

    override fun restorePlaybackState(episode: Episode, positionMs: Long) {
        Log.d(TAG, "restorePlaybackState() - episode: ${episode.title}, position: $positionMs")

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
                        Log.d(TAG, "Playback finished")
                        pause()
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating position", e)
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
     * Get the audio file path for an episode
     * Checks download cache first, downloads to temp file if needed
     */
    private suspend fun getAudioFilePath(episode: Episode): String? {
        // Check if episode is downloaded
        val downloadedFile = getDownloadedFile(episode)
        if (downloadedFile != null && downloadedFile.exists()) {
            Log.d(TAG, "Using downloaded file: ${downloadedFile.absolutePath}")
            return downloadedFile.absolutePath
        }

        // Download to temporary file for streaming
        Log.i(TAG, "Episode not cached, downloading from URL: ${episode.audioUrl}")
        return downloadToTempFile(episode)
    }

    /**
     * Download episode to temporary cache file for playback
     */
    private suspend fun downloadToTempFile(episode: Episode): String? = withContext(Dispatchers.IO) {
        try {
            // Create streaming cache directory
            val streamingCacheDir = File(context.cacheDir, "streaming")
            if (!streamingCacheDir.exists()) {
                streamingCacheDir.mkdirs()
            }

            // Use episode ID as filename
            val tempFile = File(streamingCacheDir, "${episode.id}.mp3")

            // If file already exists in streaming cache, use it
            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d(TAG, "Using existing streaming cache: ${tempFile.absolutePath}")
                return@withContext tempFile.absolutePath
            }

            Log.d(TAG, "Downloading to temp file: ${tempFile.absolutePath}")

            // Download from URL
            val url = URL(episode.audioUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 30000 // 30 seconds

            connection.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Download complete: ${tempFile.length()} bytes")
            tempFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download episode", e)
            null
        }
    }

    /**
     * Get the downloaded file for an episode
     */
    private fun getDownloadedFile(episode: Episode): File? {
        // Check standard download location
        val downloadsDir = File(context.filesDir, "downloads")
        val episodeFile = File(downloadsDir, "${episode.id}.mp3")

        if (episodeFile.exists()) {
            return episodeFile
        }

        // Check external cache
        val externalDir = context.externalCacheDir
        if (externalDir != null) {
            val externalFile = File(externalDir, "podcasts/${episode.id}.mp3")
            if (externalFile.exists()) {
                return externalFile
            }
        }

        return null
    }

    /**
     * Clean up old streaming cache files
     * Keeps only files accessed within the last 24 hours
     */
    private fun cleanupStreamingCache() {
        try {
            val streamingCacheDir = File(context.cacheDir, "streaming")
            if (!streamingCacheDir.exists()) return

            val now = System.currentTimeMillis()
            val maxAge = 24 * 60 * 60 * 1000L // 24 hours

            streamingCacheDir.listFiles()?.forEach { file ->
                if (file.isFile && now - file.lastModified() > maxAge) {
                    Log.d(TAG, "Deleting old cache file: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup streaming cache", e)
        }
    }

    /**
     * Clean up resources when player is no longer needed
     */
    fun release() {
        Log.d(TAG, "Releasing RustPodcastPlayer")
        stopPositionUpdates()
        coroutineScope.cancel()
        rustPlayer.release()
        cleanupStreamingCache()
    }
}
