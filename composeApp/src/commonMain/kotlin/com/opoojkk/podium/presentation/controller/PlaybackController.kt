package com.opoojkk.podium.presentation.controller

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackProgress
import com.opoojkk.podium.data.model.SleepTimerDuration
import com.opoojkk.podium.data.model.SleepTimerState
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.platform.fileSizeInBytes
import com.opoojkk.podium.player.PodcastPlayer
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Controller responsible for playback-related operations including
 * play/pause/stop, seeking, playback speed, and sleep timer management.
 */
class PlaybackController(
    private val repository: PodcastRepository,
    private val player: PodcastPlayer,
    private val scope: CoroutineScope,
) {
    // Expose player state
    val playbackState: StateFlow<com.opoojkk.podium.data.model.PlaybackState> = player.state

    // Sleep timer state
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()
    private var sleepTimerJob: Job? = null
    var onSleepTimerComplete: (() -> Unit)? = null

    private var playbackSaveJob: Job? = null

    // Download state for resolving cached files
    private var downloads: StateFlow<Map<String, DownloadStatus>> = MutableStateFlow(emptyMap())

    init {
        // Load last played episode
        scope.launch {
            val lastPlayed = repository.getLastPlayedEpisode()
            if (lastPlayed != null) {
                val (episode, progress) = lastPlayed
                Logger.d("PlaybackController") { "🎵 Restoring last played episode: ${episode.title} at ${progress.positionMs}ms" }
                player.restorePlaybackState(episode, progress.positionMs)
            }
        }

        // Monitor playback state and auto-save progress
        scope.launch {
            player.state.collect { state ->
                if (state.episode != null && state.isPlaying) {
                    // Start periodic save task
                    if (playbackSaveJob?.isActive != true) {
                        playbackSaveJob = scope.launch {
                            while (state.isPlaying) {
                                kotlinx.coroutines.delay(10_000) // Save every 10 seconds
                                val currentState = player.state.value
                                if (currentState.episode != null) {
                                    repository.savePlayback(
                                        PlaybackProgress(
                                            episodeId = currentState.episode.id,
                                            positionMs = currentState.positionMs,
                                            durationMs = currentState.durationMs ?: currentState.episode.duration,
                                            updatedAt = Clock.System.now(),
                                        ),
                                    )
                                    Logger.d("PlaybackController") { "🎵 Auto-saved playback progress: ${currentState.positionMs}ms" }
                                }
                            }
                        }
                    }
                } else {
                    // Stop auto-save when playback stops
                    playbackSaveJob?.cancel()
                    playbackSaveJob = null
                }
            }
        }
    }

    /**
     * Set downloads state flow for resolving cached files.
     */
    fun setDownloads(downloadsFlow: StateFlow<Map<String, DownloadStatus>>) {
        this.downloads = downloadsFlow
    }

    /**
     * Play an episode from the beginning or from saved progress.
     */
    fun playEpisode(episode: Episode) {
        Logger.d("PlaybackController") { "🎵 playEpisode called for: ${episode.title}" }
        Logger.d("PlaybackController") { "🎵 Audio URL: ${episode.audioUrl}" }
        scope.launch {
            val (episodeToPlay, cachePath) = resolvePlaybackEpisode(episode)
            if (cachePath != null) {
                Logger.d("PlaybackController") { "🎵 Playing cached file at $cachePath" }
            }
            val progress = repository.playbackForEpisode(episode.id)
            val startPosition = progress?.positionMs ?: 0L
            Logger.d("PlaybackController") { "🎵 Starting playback at position: $startPosition" }
            player.play(episodeToPlay, startPosition)
            repository.savePlayback(
                PlaybackProgress(
                    episodeId = episode.id,
                    positionMs = startPosition,
                    durationMs = episode.duration,
                    updatedAt = Clock.System.now(),
                ),
            )
        }
    }

    /**
     * Resume playback.
     */
    fun resume() = player.resume()

    /**
     * Pause playback.
     */
    fun pause() = player.pause()

    /**
     * Stop playback.
     */
    fun stop() = player.stop()

    /**
     * Seek to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    /**
     * Seek by a relative delta in milliseconds.
     */
    fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

    /**
     * Set playback speed.
     */
    fun setPlaybackSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    /**
     * Start a sleep timer with the specified duration.
     */
    fun startSleepTimer(duration: SleepTimerDuration) {
        Logger.d("PlaybackController") { "⏰ Starting sleep timer for ${duration.displayName}" }
        cancelSleepTimer() // Cancel any existing timer

        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            duration = duration,
            remainingMs = duration.milliseconds
        )

        sleepTimerJob = scope.launch {
            val startTime = Clock.System.now().toEpochMilliseconds()
            val endTime = startTime + duration.milliseconds

            while (true) {
                kotlinx.coroutines.delay(1000) // Update every second
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val remaining = (endTime - currentTime).coerceAtLeast(0)

                _sleepTimerState.value = _sleepTimerState.value.copy(
                    remainingMs = remaining
                )

                if (remaining <= 0) {
                    Logger.d("PlaybackController") { "⏰ Sleep timer completed" }
                    onTimerComplete()
                    break
                }
            }
        }
    }

    /**
     * Cancel the currently active sleep timer.
     */
    fun cancelSleepTimer() {
        Logger.d("PlaybackController") { "⏰ Cancelling sleep timer" }
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerState.value = SleepTimerState()
    }

    private fun onTimerComplete() {
        // Stop playback
        player.pause()

        // Save current progress
        scope.launch {
            val currentState = player.state.value
            if (currentState.episode != null) {
                repository.savePlayback(
                    PlaybackProgress(
                        episodeId = currentState.episode.id,
                        positionMs = currentState.positionMs,
                        durationMs = currentState.durationMs ?: currentState.episode.duration,
                        updatedAt = Clock.System.now(),
                    ),
                )
            }
        }

        // Reset timer state
        _sleepTimerState.value = SleepTimerState()

        // Trigger app exit callback
        onSleepTimerComplete?.invoke()
    }

    private fun resolvePlaybackEpisode(episode: Episode): Pair<Episode, String?> {
        val completed = downloads.value[episode.id] as? DownloadStatus.Completed
        val filePath = completed?.filePath?.takeIf { it.isNotBlank() }
        if (filePath != null) {
            val exists = fileSizeInBytes(filePath) != null
            if (exists) {
                return episode.copy(audioUrl = toPlayableUri(filePath)) to filePath
            }
        }
        return episode to null
    }

    private fun toPlayableUri(path: String): String = when {
        path.startsWith("file://") -> path
        path.startsWith("content://") -> path
        path.startsWith("/") -> "file://$path"
        else -> path
    }
}
