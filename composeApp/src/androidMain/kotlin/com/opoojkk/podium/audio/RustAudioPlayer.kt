package com.opoojkk.podium.audio

import android.util.Log

/**
 * Kotlin wrapper for Rust audio player
 * Provides a type-safe interface to the native Rust implementation
 */
class RustAudioPlayer {

    companion object {
        private const val TAG = "RustAudioPlayer"

        // Load native library
        init {
            try {
                // Load C++ standard library first (required by Oboe)
                // This ensures libc++ is available before loading our native library
                try {
                    System.loadLibrary("c++_shared")
                    Log.d(TAG, "C++ standard library loaded")
                } catch (e: UnsatisfiedLinkError) {
                    // If c++_shared is not available, try to continue
                    // (system may already have it loaded, or we're using static linking)
                    Log.w(TAG, "Could not load c++_shared, will try to continue: ${e.message}")
                }

                // Load our Rust audio player library (podium-audio FFI)
                System.loadLibrary("podium_audio_player")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                Log.e(TAG, "Make sure you have run: cd podium-audio && ./build.sh")
                throw e
            }
        }

        // Native methods
        @JvmStatic
        private external fun nativeCreate(): Long

        @JvmStatic
        private external fun nativeLoadFile(playerId: Long, path: String): Int

        @JvmStatic
        private external fun nativeLoadUrl(playerId: Long, url: String): Int

        @JvmStatic
        private external fun nativeLoadBuffer(playerId: Long, buffer: ByteArray): Int

        @JvmStatic
        private external fun nativePlay(playerId: Long): Int

        @JvmStatic
        private external fun nativePause(playerId: Long): Int

        @JvmStatic
        private external fun nativeStop(playerId: Long): Int

        @JvmStatic
        private external fun nativeSeek(playerId: Long, positionMs: Long): Int

        @JvmStatic
        private external fun nativeSetVolume(playerId: Long, volume: Float): Int

        @JvmStatic
        private external fun nativeGetPosition(playerId: Long): Long

        @JvmStatic
        private external fun nativeGetDuration(playerId: Long): Long

        @JvmStatic
        private external fun nativeGetState(playerId: Long): Int

        @JvmStatic
        private external fun nativeRelease(playerId: Long): Int
    }

    /**
     * Player state enum matching Rust implementation
     */
    enum class PlayerState(val value: Int) {
        IDLE(0),
        LOADING(1),
        READY(2),
        PLAYING(3),
        PAUSED(4),
        STOPPED(5),
        ERROR(6);

        companion object {
            fun fromInt(value: Int): PlayerState {
                return values().firstOrNull { it.value == value } ?: ERROR
            }
        }
    }

    /**
     * Exception thrown when native operations fail
     */
    class AudioPlayerException(message: String) : Exception(message)

    // Player instance handle (ID from Rust)
    private var playerId: Long = -1
    private var isReleased = false

    init {
        playerId = nativeCreate()
        if (playerId < 0) {
            throw AudioPlayerException("Failed to create native audio player")
        }
        Log.d(TAG, "Audio player created with ID: $playerId")
    }

    /**
     * Load audio from file path
     * @param path Absolute file path to audio file
     * @throws AudioPlayerException if loading fails
     */
    fun loadFile(path: String) {
        checkNotReleased()
        Log.d(TAG, "Loading file: $path")

        val result = nativeLoadFile(playerId, path)
        if (result != 0) {
            throw AudioPlayerException("Failed to load file: $path")
        }
    }

    /**
     * Load audio from URL (HTTP/HTTPS streaming)
     * @param url URL to audio file
     * @throws AudioPlayerException if loading fails
     */
    fun loadUrl(url: String) {
        checkNotReleased()
        Log.d(TAG, "Loading URL: $url")

        val result = nativeLoadUrl(playerId, url)
        if (result != 0) {
            throw AudioPlayerException("Failed to load URL: $url")
        }
    }

    /**
     * Load audio from byte buffer
     * @param buffer Audio file data as byte array
     * @throws AudioPlayerException if loading fails
     */
    fun loadBuffer(buffer: ByteArray) {
        checkNotReleased()
        Log.d(TAG, "Loading buffer: ${buffer.size} bytes")

        val result = nativeLoadBuffer(playerId, buffer)
        if (result != 0) {
            throw AudioPlayerException("Failed to load buffer")
        }
    }

    /**
     * Start or resume playback
     * @throws AudioPlayerException if play fails
     */
    fun play() {
        checkNotReleased()
        Log.d(TAG, "Play")

        val result = nativePlay(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to start playback")
        }
    }

    /**
     * Pause playback
     * @throws AudioPlayerException if pause fails
     */
    fun pause() {
        checkNotReleased()
        Log.d(TAG, "Pause")

        val result = nativePause(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to pause playback")
        }
    }

    /**
     * Stop playback and reset position
     * @throws AudioPlayerException if stop fails
     */
    fun stop() {
        checkNotReleased()
        Log.d(TAG, "Stop")

        val result = nativeStop(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to stop playback")
        }
    }

    /**
     * Seek to specific position
     * @param positionMs Position in milliseconds
     * @throws AudioPlayerException if seek fails
     */
    fun seek(positionMs: Long) {
        checkNotReleased()
        Log.d(TAG, "Seek to $positionMs ms")

        val result = nativeSeek(playerId, positionMs)
        if (result != 0) {
            throw AudioPlayerException("Failed to seek to $positionMs ms")
        }
    }

    /**
     * Set playback volume
     * @param volume Volume level (0.0 - 1.0)
     * @throws AudioPlayerException if setting volume fails
     * @throws IllegalArgumentException if volume is out of range
     */
    fun setVolume(volume: Float) {
        checkNotReleased()

        if (volume < 0.0f || volume > 1.0f) {
            throw IllegalArgumentException("Volume must be between 0.0 and 1.0, got $volume")
        }

        Log.d(TAG, "Set volume to $volume")

        val result = nativeSetVolume(playerId, volume)
        if (result != 0) {
            throw AudioPlayerException("Failed to set volume to $volume")
        }
    }

    /**
     * Get current playback position
     * @return Current position in milliseconds
     */
    fun getPosition(): Long {
        checkNotReleased()
        return nativeGetPosition(playerId)
    }

    /**
     * Get audio duration
     * @return Duration in milliseconds, or 0 if unknown
     */
    fun getDuration(): Long {
        checkNotReleased()
        return nativeGetDuration(playerId)
    }

    /**
     * Get current player state
     * @return Current PlayerState
     */
    fun getState(): PlayerState {
        checkNotReleased()
        val stateValue = nativeGetState(playerId)
        return PlayerState.fromInt(stateValue)
    }

    /**
     * Check if player is currently playing
     * @return true if playing, false otherwise
     */
    fun isPlaying(): Boolean {
        return getState() == PlayerState.PLAYING
    }

    /**
     * Check if player is paused
     * @return true if paused, false otherwise
     */
    fun isPaused(): Boolean {
        return getState() == PlayerState.PAUSED
    }

    /**
     * Release all player resources
     * Must be called when done with the player
     */
    fun release() {
        if (!isReleased) {
            Log.d(TAG, "Releasing player $playerId")
            nativeRelease(playerId)
            isReleased = true
            playerId = -1
        }
    }

    /**
     * Check if player has been released
     */
    private fun checkNotReleased() {
        if (isReleased) {
            throw AudioPlayerException("Player has been released")
        }
    }

    /**
     * Auto-release when garbage collected
     */
    protected fun finalize() {
        if (!isReleased) {
            Log.w(TAG, "Player $playerId was not explicitly released, releasing in finalizer")
            release()
        }
    }
}

/**
 * Convenience extension function to use player with automatic resource management
 */
inline fun <T> useAudioPlayer(block: (RustAudioPlayer) -> T): T {
    val player = RustAudioPlayer()
    try {
        return block(player)
    } finally {
        player.release()
    }
}
