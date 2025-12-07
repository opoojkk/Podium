package com.opoojkk.podium.audio

import java.nio.file.Files

/**
 * JVM wrapper for Rust audio player
 * Provides a type-safe interface to the native Rust implementation
 */
class RustAudioPlayerJvm {

    companion object {
        private const val TAG = "RustAudioPlayerJvm"
        private var libraryLoaded = false

        // Load native library
        init {
            try {
                loadNativeLibrary()
                libraryLoaded = true
                println("$TAG: Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                println("$TAG: Failed to load native library: ${e.message}")
                throw e
            }
        }

        /**
         * Load the native library based on the current OS and architecture.
         */
        private fun loadNativeLibrary() {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val libraryPath = when {
                osName.contains("mac") || osName.contains("darwin") -> {
                    when {
                        osArch.contains("aarch64") || osArch.contains("arm") ->
                            "darwin-aarch64/libpodium_audio_player.dylib"
                        else ->
                            "darwin-x86_64/libpodium_audio_player.dylib"
                    }
                }
                osName.contains("windows") -> {
                    "windows-x86_64/podium_audio_player.dll"
                }
                osName.contains("linux") -> {
                    when {
                        osArch.contains("aarch64") || osArch.contains("arm") ->
                            "linux-aarch64/libpodium_audio_player.so"
                        else ->
                            "linux-x86_64/libpodium_audio_player.so"
                    }
                }
                else -> throw UnsatisfiedLinkError("Unsupported OS: $osName")
            }

            // Extract library from resources to a temporary file
            val inputStream = RustAudioPlayerJvm::class.java.classLoader.getResourceAsStream(libraryPath)
                ?: throw UnsatisfiedLinkError("Library not found in resources: $libraryPath")

            val tempFile = Files.createTempFile("libpodium_audio_player", getLibraryExtension()).toFile()
            tempFile.deleteOnExit()

            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Set executable permissions on Unix-like systems
            if (!osName.contains("windows")) {
                tempFile.setExecutable(true)
                tempFile.setReadable(true)
            }

            System.load(tempFile.absolutePath)
            println("$TAG: Successfully loaded native library from: ${tempFile.absolutePath}")
        }

        private fun getLibraryExtension(): String {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.contains("mac") || osName.contains("darwin") -> ".dylib"
                osName.contains("windows") -> ".dll"
                else -> ".so"
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
        println("$TAG: Audio player created with ID: $playerId")
    }

    /**
     * Load audio from file path
     */
    fun loadFile(path: String) {
        checkNotReleased()
        println("$TAG: Loading file: $path")

        val result = nativeLoadFile(playerId, path)
        if (result != 0) {
            throw AudioPlayerException("Failed to load file: $path")
        }
    }

    /**
     * Load audio from URL (HTTP/HTTPS streaming)
     */
    fun loadUrl(url: String) {
        checkNotReleased()
        println("$TAG: Loading URL: $url")

        val result = nativeLoadUrl(playerId, url)
        if (result != 0) {
            throw AudioPlayerException("Failed to load URL: $url")
        }
    }

    /**
     * Load audio from byte buffer
     */
    fun loadBuffer(buffer: ByteArray) {
        checkNotReleased()
        println("$TAG: Loading buffer: ${buffer.size} bytes")

        val result = nativeLoadBuffer(playerId, buffer)
        if (result != 0) {
            throw AudioPlayerException("Failed to load buffer")
        }
    }

    /**
     * Start or resume playback
     */
    fun play() {
        checkNotReleased()
        println("$TAG: Play")

        val result = nativePlay(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to play")
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        checkNotReleased()
        println("$TAG: Pause")

        val result = nativePause(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to pause")
        }
    }

    /**
     * Stop playback
     */
    fun stop() {
        checkNotReleased()
        println("$TAG: Stop")

        val result = nativeStop(playerId)
        if (result != 0) {
            throw AudioPlayerException("Failed to stop")
        }
    }

    /**
     * Seek to position in milliseconds
     */
    fun seek(positionMs: Long) {
        checkNotReleased()
        println("$TAG: Seek to $positionMs ms")

        val result = nativeSeek(playerId, positionMs)
        if (result != 0) {
            throw AudioPlayerException("Failed to seek")
        }
    }

    /**
     * Set volume (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        checkNotReleased()
        val clampedVolume = volume.coerceIn(0f, 1f)

        val result = nativeSetVolume(playerId, clampedVolume)
        if (result != 0) {
            throw AudioPlayerException("Failed to set volume")
        }
    }

    /**
     * Get current position in milliseconds
     */
    fun getPosition(): Long {
        checkNotReleased()
        return nativeGetPosition(playerId)
    }

    /**
     * Get duration in milliseconds
     */
    fun getDuration(): Long {
        checkNotReleased()
        return nativeGetDuration(playerId)
    }

    /**
     * Get current player state
     */
    fun getState(): PlayerState {
        checkNotReleased()
        val stateValue = nativeGetState(playerId)
        return PlayerState.fromInt(stateValue)
    }

    /**
     * Release player resources
     */
    fun release() {
        if (!isReleased) {
            println("$TAG: Releasing player $playerId")
            nativeRelease(playerId)
            isReleased = true
        }
    }

    private fun checkNotReleased() {
        if (isReleased) {
            throw IllegalStateException("Player has been released")
        }
    }
}
