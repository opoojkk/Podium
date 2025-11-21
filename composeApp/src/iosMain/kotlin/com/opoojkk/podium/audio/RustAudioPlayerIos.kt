package com.opoojkk.podium.audio

import com.opoojkk.podium.rust.*
import kotlinx.cinterop.*

/**
 * iOS wrapper for Rust audio player using C FFI
 */
@OptIn(ExperimentalForeignApi::class)
class RustAudioPlayerIos {

    private var playerId: Long = -1
    private var isReleased = false

    enum class PlayerState {
        IDLE,
        LOADING,
        READY,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR;

        companion object {
            fun fromInt(value: Int): PlayerState = when (value) {
                0 -> IDLE
                1 -> LOADING
                2 -> READY
                3 -> PLAYING
                4 -> PAUSED
                5 -> STOPPED
                6 -> ERROR
                else -> IDLE
            }
        }
    }

    init {
        playerId = rust_audio_player_create()
        if (playerId < 0) {
            throw AudioPlayerException("Failed to create Rust audio player")
        }
    }

    /**
     * Load audio file from path
     */
    fun loadFile(path: String) {
        checkNotReleased()

        val result = path.useCString { cPath ->
            rust_audio_player_load_file(playerId, cPath)
        }

        if (result != 0) {
            throw AudioPlayerException("Failed to load file: $path")
        }
    }

    /**
     * Load audio from URL
     */
    fun loadUrl(url: String) {
        checkNotReleased()

        val result = url.useCString { cUrl ->
            rust_audio_player_load_url(playerId, cUrl)
        }

        if (result != 0) {
            throw AudioPlayerException("Failed to load URL: $url")
        }
    }

    /**
     * Start or resume playback
     */
    fun play() {
        checkNotReleased()

        val result = rust_audio_player_play(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to play")
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        checkNotReleased()

        val result = rust_audio_player_pause(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to pause")
        }
    }

    /**
     * Stop playback
     */
    fun stop() {
        checkNotReleased()

        val result = rust_audio_player_stop(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to stop")
        }
    }

    /**
     * Seek to position in milliseconds
     */
    fun seek(positionMs: Long) {
        checkNotReleased()

        val result = rust_audio_player_seek(playerId, positionMs)
        if (result != 0) {
            throw AudioPlayerException("Failed to seek to $positionMs ms")
        }
    }

    /**
     * Get current playback position in milliseconds
     */
    fun getPosition(): Long {
        checkNotReleased()
        return rust_audio_player_get_position(playerId)
    }

    /**
     * Get audio duration in milliseconds
     */
    fun getDuration(): Long {
        checkNotReleased()
        return rust_audio_player_get_duration(playerId)
    }

    /**
     * Get current player state
     */
    fun getState(): PlayerState {
        checkNotReleased()
        val stateInt = rust_audio_player_get_state(playerId)
        return PlayerState.fromInt(stateInt)
    }

    /**
     * Release player resources
     */
    fun release() {
        if (!isReleased && playerId >= 0) {
            rust_audio_player_release(playerId)
            isReleased = true
            playerId = -1
        }
    }

    private fun checkNotReleased() {
        if (isReleased) {
            throw IllegalStateException("Audio player has been released")
        }
    }
}

/**
 * Exception thrown by audio player
 */
class AudioPlayerException(message: String) : Exception(message)
