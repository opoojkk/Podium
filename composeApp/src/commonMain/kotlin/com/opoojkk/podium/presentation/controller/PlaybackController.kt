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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
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
    companion object {
        /** Auto-save interval in milliseconds (10 seconds) */
        private const val AUTO_SAVE_INTERVAL_MS = 10_000L

        /** Sleep timer update interval in milliseconds (1 second) */
        private const val SLEEP_TIMER_UPDATE_INTERVAL_MS = 1_000L
    }

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
            Logger.d("PlaybackController") { "🎵 Loading last played episode from database..." }
            val lastPlayed = repository.getLastPlayedEpisode()
            if (lastPlayed != null) {
                val (episode, progress) = lastPlayed
                Logger.d("PlaybackController") { "🎵 Found last played episode: ${episode.title} at ${progress.positionMs}ms" }
                player.restorePlaybackState(episode, progress.positionMs)
                Logger.d("PlaybackController") { "🎵 Playback state restored successfully" }
            } else {
                Logger.d("PlaybackController") { "🎵 No last played episode found in database" }
            }
        }

        // Monitor playback state and auto-save progress
        scope.launch {
            player.state
                .distinctUntilChangedBy { it.isPlaying }
                .collect { state ->
                    if (state.episode != null && state.isPlaying) {
                        startAutoSave()
                    } else {
                        stopAutoSave()
                    }
                }
        }
    }

    /**
     * Start auto-save timer for playback progress.
     */
    private fun startAutoSave() {
        // Cancel existing job if any
        playbackSaveJob?.cancel()

        playbackSaveJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(AUTO_SAVE_INTERVAL_MS)

                // Get fresh state
                val currentState = player.state.value
                if (currentState.episode != null && currentState.isPlaying) {
                    repository.savePlayback(
                        PlaybackProgress(
                            episodeId = currentState.episode.id,
                            positionMs = currentState.positionMs,
                            durationMs = currentState.durationMs ?: currentState.episode.duration,
                            updatedAt = Clock.System.now(),
                        ),
                    )
                    Logger.d("PlaybackController") { "🎵 Auto-saved playback progress: ${currentState.positionMs}ms" }
                } else {
                    // Stop if playback stopped
                    break
                }
            }
        }
    }

    /**
     * Stop auto-save timer.
     */
    private fun stopAutoSave() {
        playbackSaveJob?.cancel()
        playbackSaveJob = null
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
     * Pause playback and save current progress.
     */
    fun pause() {
        player.pause()
        // Immediately save progress when pausing
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
                Logger.d("PlaybackController") { "🎵 Saved playback progress on pause: ${currentState.positionMs}ms" }
            }
        }
    }

    /**
     * Stop playback and save current progress.
     */
    fun stop() {
        // Save progress before stopping
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
                Logger.d("PlaybackController") { "🎵 Saved playback progress on stop: ${currentState.positionMs}ms" }
            }
        }
        player.stop()
    }

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
                kotlinx.coroutines.delay(SLEEP_TIMER_UPDATE_INTERVAL_MS)
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
