package com.opoojkk.podium.player

import com.opoojkk.podium.data.model.Episode
import com.opoojkk.podium.data.model.PlaybackState
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.JavaLayerException
import javazoom.jl.player.advanced.AdvancedPlayer
import javazoom.jl.player.advanced.PlaybackEvent
import javazoom.jl.player.advanced.PlaybackListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Desktop podcast player implementation using JLayer for MP3 and Java Sound API for other formats.
 * Inspired by Less-Player's approach with improved streaming support.
 * 
 * Features:
 * - Zero external dependencies - pure Java libraries
 * - Supports MP3 (via JLayer) and WAV (via Java Sound API)
 * - Direct HTTP/HTTPS streaming support
 * - Playback controls (play, pause, resume, stop)
 * - Real-time position tracking
 * 
 * Note: Seeking is limited due to streaming nature. For best results with seeking,
 * the audio would need to be cached or downloaded first.
 */
class DesktopPodcastPlayer : PodcastPlayer {

    private val _state = MutableStateFlow(PlaybackState(null, 0L, false, null, false))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var playerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var currentEpisode: Episode? = null
    private var currentPlayer: AdvancedPlayer? = null
    private var currentLine: SourceDataLine? = null
    
    @Volatile
    private var isPlaying = false
    @Volatile
    private var isPaused = false
    @Volatile
    private var shouldStop = false
    
    private var startPositionMs: Long = 0
    private var pausedAtMs: Long = 0
    private var playbackStartTime: Long = 0
    private var detectedDurationMs: Long? = null

    override suspend fun play(episode: Episode, startPositionMs: Long) {
        println("🎵 Desktop Player: Starting playback for episode: ${episode.title}")
        println("🎵 Desktop Player: Audio URL: ${episode.audioUrl}")
        
        this.startPositionMs = startPositionMs
        
        withContext(Dispatchers.IO) {
            try {
                // Check if we're resuming the same episode to avoid UI flicker
                val isResuming = isPaused && currentEpisode == episode
                
                // Stop any existing playback, but preserve episode info when resuming
                stop(clearEpisode = !isResuming)
                
                currentEpisode = episode
                shouldStop = false
                isPaused = false
                
                // Only reset detected duration if it's a new episode
                if (!isResuming) {
                    detectedDurationMs = null
                }
                
                // Update state immediately to show we're loading the episode
                updateState()
                
                // Determine audio format from URL
                val isMp3 = episode.audioUrl.lowercase().contains(".mp3") ||
                           episode.audioUrl.lowercase().contains("mpeg")
                
                if (isMp3) {
                    playMp3Stream(episode.audioUrl)
                } else {
                    playOtherFormat(episode.audioUrl)
                }
                
            } catch (e: Exception) {
                println("🎵 Desktop Player: Error occurred: ${e.message}")
                e.printStackTrace()
                currentEpisode = null
                _state.value = PlaybackState(null, 0L, false, null)
            }
        }
    }

    private suspend fun playMp3Stream(url: String) {
        // Try to detect MP3 duration before playback if Episode doesn't have it
        if (currentEpisode?.duration == null) {
            tryDetectMp3Duration(url)
        }
        
        playerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection()
                connection.setRequestProperty("User-Agent", "Podium/1.0")
                val inputStream = BufferedInputStream(connection.getInputStream())
                
                val player = AdvancedPlayer(inputStream)
                currentPlayer = player
                
                player.setPlayBackListener(object : PlaybackListener() {
                    override fun playbackStarted(evt: PlaybackEvent) {
                        println("🎵 Desktop Player: Playback started")
                        if (!shouldStop) {
                            isPlaying = true
                            isPaused = false
                            playbackStartTime = System.currentTimeMillis()
                            updateState()
                            startPositionUpdates()
                        }
                    }
                    
                    override fun playbackFinished(evt: PlaybackEvent) {
                        println("🎵 Desktop Player: Playback finished (shouldStop=$shouldStop, isPaused=$isPaused)")
                        if (!isPaused) {
                            isPlaying = false
                        }
                        stopPositionUpdates()
                        if (!shouldStop && !isPaused) {
                            currentEpisode = null
                            _state.value = PlaybackState(null, 0L, false, null)
                        }
                    }
                })
                
                println("🎵 Desktop Player: Starting MP3 playback...")
                // Start playback (this blocks until playback finishes or is stopped)
                player.play()
                
            } catch (e: JavaLayerException) {
                println("🎵 Desktop Player: JavaLayer error: ${e.message}")
                e.printStackTrace()
                isPlaying = false
                isPaused = false
                _state.value = PlaybackState(null, 0L, false, null)
            } catch (e: Exception) {
                println("🎵 Desktop Player: Error: ${e.message}")
                e.printStackTrace()
                isPlaying = false
                isPaused = false
                _state.value = PlaybackState(null, 0L, false, null)
            } finally {
                currentPlayer = null
            }
        }
    }

    /**
     * Try to detect MP3 duration by analyzing the stream headers.
     * This works best for files with constant bitrate or proper VBR headers.
     */
    private suspend fun tryDetectMp3Duration(url: String) {
        withContext(Dispatchers.IO) {
            var bitstream: Bitstream? = null
            try {
                println("🎵 Desktop Player: Attempting to detect MP3 duration...")
                val connection = URL(url).openConnection()
                connection.setRequestProperty("User-Agent", "Podium/1.0")
                
                // Try to get content length for estimation
                val contentLength = connection.contentLengthLong
                
                val inputStream = BufferedInputStream(connection.getInputStream())
                bitstream = Bitstream(inputStream)
                
                // Read first frame to get bitrate
                val firstHeader = bitstream.readFrame()
                if (firstHeader != null) {
                    val bitrate = firstHeader.bitrate() // in bps
                    
                    if (contentLength > 0 && bitrate > 0) {
                        // Estimate duration: (file size in bytes * 8 bits/byte) / bitrate
                        val estimatedDurationSeconds = (contentLength * 8.0) / bitrate
                        detectedDurationMs = (estimatedDurationSeconds * 1000).toLong()
                        println("🎵 Desktop Player: Estimated MP3 duration: ${detectedDurationMs}ms (based on file size: ${contentLength} bytes, bitrate: ${bitrate} bps)")
                    } else {
                        println("🎵 Desktop Player: Could not estimate duration (contentLength: $contentLength, bitrate: $bitrate)")
                    }
                }
                
                bitstream.close()
            } catch (e: Exception) {
                println("🎵 Desktop Player: Could not detect MP3 duration: ${e.message}")
            } finally {
                try {
                    bitstream?.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }
    }

    private suspend fun playOtherFormat(url: String) {
        playerJob = CoroutineScope(Dispatchers.IO).launch {
            var line: SourceDataLine? = null
            try {
                val connection = URL(url).openConnection()
                connection.setRequestProperty("User-Agent", "Podium/1.0")
                val inputStream = BufferedInputStream(connection.getInputStream())
                
                val audioInputStream = AudioSystem.getAudioInputStream(inputStream)
                val format = audioInputStream.format
                
                // Convert to PCM if needed
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.sampleRate,
                    16,
                    format.channels,
                    format.channels * 2,
                    format.sampleRate,
                    false
                )
                
                val decodedStream: AudioInputStream = if (format.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                    AudioSystem.getAudioInputStream(decodedFormat, audioInputStream)
                } else {
                    audioInputStream
                }
                
                // Try to detect duration from audio stream if Episode doesn't have it
                if (currentEpisode?.duration == null) {
                    try {
                        val frameLength = decodedStream.frameLength
                        val frameRate = decodedStream.format.frameRate
                        if (frameLength > 0 && frameRate > 0) {
                            detectedDurationMs = ((frameLength / frameRate) * 1000).toLong()
                            println("🎵 Desktop Player: Detected duration: ${detectedDurationMs}ms")
                        } else {
                            println("🎵 Desktop Player: Could not detect duration (frameLength: $frameLength, frameRate: $frameRate)")
                        }
                    } catch (e: Exception) {
                        println("🎵 Desktop Player: Could not detect duration: ${e.message}")
                    }
                } else {
                    println("🎵 Desktop Player: Using duration from Episode data: ${currentEpisode?.duration}ms")
                }
                
                line = AudioSystem.getSourceDataLine(decodedStream.format)
                currentLine = line
                line.open(decodedStream.format)
                line.start()
                
                isPlaying = true
                isPaused = false
                playbackStartTime = System.currentTimeMillis()
                updateState()
                startPositionUpdates()
                
                println("🎵 Desktop Player: Streaming audio...")
                
                val buffer = ByteArray(4096)
                var bytesRead = 0
                
                while (!shouldStop && decodedStream.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                    if (!isPaused) {
                        line.write(buffer, 0, bytesRead)
                    } else {
                        // Wait while paused
                        delay(100)
                    }
                }
                
                line.drain()
                
            } catch (e: Exception) {
                println("🎵 Desktop Player: Error playing audio: ${e.message}")
                e.printStackTrace()
                _state.value = PlaybackState(null, 0L, false, null)
            } finally {
                line?.stop()
                line?.close()
                currentLine = null
                if (!isPaused) {
                    isPlaying = false
                }
                stopPositionUpdates()
            }
        }
    }

    override fun pause() {
        if (isPlaying && !isPaused) {
            println("🎵 Desktop Player: Pausing playback")
            pausedAtMs = position()
            isPaused = true
            isPlaying = false
            
            // Stop position updates
            stopPositionUpdates()
            
            // For MP3, we need to close the player since it doesn't support pause
            try {
                currentPlayer?.close()
            } catch (e: Exception) {
                println("🎵 Desktop Player: Error closing player: ${e.message}")
            }
            playerJob?.cancel()
            
            // For other formats, line pause is handled in the playback loop
            
            // Update state to reflect pause
            updateState()
            println("🎵 Desktop Player: Paused at ${pausedAtMs}ms")
        }
    }

    override fun resume() {
        if (currentEpisode != null) {
            println("🎵 Desktop Player: Resuming playback from ${pausedAtMs}ms")
            val episode = currentEpisode!!
            
            // 如果播放器还未初始化（刚恢复状态），则需要从头初始化
            if (playerJob == null || !playerJob!!.isActive) {
                println("🎵 Desktop Player: Player not initialized, starting playback")
                isPaused = false
                CoroutineScope(Dispatchers.IO).launch {
                    play(episode, pausedAtMs)
                }
            } else if (isPaused) {
                // 播放器已初始化，只是暂停了
                isPaused = false
                
                // Launch a coroutine to resume playback
                CoroutineScope(Dispatchers.IO).launch {
                    // For MP3, we need to restart from the paused position
                    // For other formats, the playback loop will continue
                    if (episode.audioUrl.lowercase().contains(".mp3") ||
                        episode.audioUrl.lowercase().contains("mpeg")) {
                        // Restart MP3 playback from paused position
                        startPositionMs = pausedAtMs
                        play(episode, pausedAtMs)
                    } else {
                        // For other formats, just update the state and timing
                        isPlaying = true
                        playbackStartTime = System.currentTimeMillis() - pausedAtMs
                        updateState()
                        startPositionUpdates()
                    }
                }
            }
        }
    }

    override fun stop() {
        stop(clearEpisode = true)
    }
    
    private fun stop(clearEpisode: Boolean) {
        println("🎵 Desktop Player: Stopping playback (clearEpisode=$clearEpisode)")
        shouldStop = true
        isPaused = false
        isPlaying = false
        stopPositionUpdates()
        
        // Close current player/line safely
        try {
            currentPlayer?.close()
        } catch (e: Exception) {
            println("🎵 Desktop Player: Error closing player: ${e.message}")
        }
        
        try {
            currentLine?.stop()
            currentLine?.close()
        } catch (e: Exception) {
            println("🎵 Desktop Player: Error closing line: ${e.message}")
        }
        
        playerJob?.cancel()
        playerJob = null
        
        currentPlayer = null
        currentLine = null
        
        if (clearEpisode) {
            currentEpisode = null
            startPositionMs = 0
            pausedAtMs = 0
            detectedDurationMs = null
            _state.value = PlaybackState(null, 0L, false, null)
        }
    }

    override fun seekTo(positionMs: Long) {
        // Note: Seeking in streaming mode is complex and would require
        // re-establishing the connection and skipping to the position.
        // For simplicity, this is a placeholder that would need enhancement.
        println("🎵 Desktop Player: Seeking to ${positionMs}ms (limited support in streaming mode)")
        // TODO: Implement seeking by restarting playback from position
    }

    override fun seekBy(deltaMs: Long) {
        val current = position()
        seekTo(current + deltaMs)
    }

    override fun restorePlaybackState(episode: Episode, positionMs: Long) {
        println("🎵 Desktop Player: Restoring playback state for episode: ${episode.title} at ${positionMs}ms")
        // 直接设置状态，不准备播放器
        // 播放器会在用户点击播放按钮时通过 play() 或 resume() 方法初始化
        currentEpisode = episode
        startPositionMs = positionMs
        pausedAtMs = positionMs
        isPaused = true
        isPlaying = false
        _state.value = PlaybackState(
            episode = episode,
            positionMs = positionMs,
            isPlaying = false,
            durationMs = episode.duration,
            isBuffering = false,
        )
    }

    private fun position(): Long {
        return if (isPlaying || isPaused) {
            val elapsed = if (isPaused) {
                pausedAtMs
            } else {
                System.currentTimeMillis() - playbackStartTime + startPositionMs
            }
            elapsed.coerceAtLeast(0L)
        } else {
            0L
        }
    }

    private fun updateState() {
        // 优先从 Episode 数据获取时长，如果没有则使用播放器检测到的时长
        val duration = currentEpisode?.duration ?: detectedDurationMs
        
        _state.value = PlaybackState(
            episode = currentEpisode,
            positionMs = position(),
            isPlaying = isPlaying,
            durationMs = duration
        )
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isPlaying) {
                updateState()
                delay(500) // Update twice per second
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * Release resources when the player is no longer needed
     */
    fun release() {
        println("🎵 Desktop Player: Releasing player resources")
        stop()
    }
}
