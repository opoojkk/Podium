package com.opoojkk.podium.presentation.controller

import com.opoojkk.podium.data.model.PlaylistItem
import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for playlist management.
 */
data class PlaylistState(
    val items: List<PlaylistItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Controller responsible for playlist-related operations including
 * adding, removing, and marking episodes as completed.
 */
class PlaylistController(
    private val repository: PodcastRepository,
    private val scope: CoroutineScope,
) {
    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()

    init {
        // Observe playlist changes from repository
        scope.launch {
            repository.observePlaylist().collect { playlistItems ->
                Logger.d("PlaylistController") { "📋 Playlist updated: ${playlistItems.size} items" }
                _playlistState.value = _playlistState.value.copy(
                    items = playlistItems,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Add an episode to the playlist.
     */
    fun addToPlaylist(episodeId: String) {
        scope.launch {
            repository.addToPlaylist(episodeId)
            Logger.d("PlaylistController") { "➕ Added episode $episodeId to playlist" }
        }
    }

    /**
     * Remove an episode from the playlist.
     */
    fun removeFromPlaylist(episodeId: String) {
        scope.launch {
            repository.removeFromPlaylist(episodeId)
            Logger.d("PlaylistController") { "🗑️ Removed episode $episodeId from playlist" }
        }
    }

    /**
     * Mark an episode as completed.
     */
    fun markEpisodeCompleted(episodeId: String) {
        scope.launch {
            repository.markEpisodeCompleted(episodeId)
            Logger.i("PlaylistController") { "✅ Marked episode $episodeId as completed" }
        }
    }
}
