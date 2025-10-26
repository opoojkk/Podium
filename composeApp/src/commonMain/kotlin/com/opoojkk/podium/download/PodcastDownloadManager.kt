package com.opoojkk.podium.download

import com.opoojkk.podium.data.model.DownloadStatus
import com.opoojkk.podium.data.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface PodcastDownloadManager {
    val downloads: StateFlow<Map<String, DownloadStatus>>
    val autoDownloadEnabled: StateFlow<Boolean>

    fun setAutoDownload(enabled: Boolean)
    fun isAutoDownloadEnabled(): Boolean = autoDownloadEnabled.value
    fun cancel(episodeId: String)
    fun enqueue(episode: Episode, auto: Boolean = false)
    fun clearDownloads(episodeIds: Collection<String>)
}

class DefaultPodcastDownloadManager(
    private val scope: CoroutineScope,
    private val onStatusChanged: (DownloadStatus) -> Unit = {},
) : PodcastDownloadManager {

    private val _downloads = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    private val _autoDownloadEnabled = MutableStateFlow(true)

    override val downloads: StateFlow<Map<String, DownloadStatus>> = _downloads.asStateFlow()
    override val autoDownloadEnabled: StateFlow<Boolean> = _autoDownloadEnabled.asStateFlow()

    override fun setAutoDownload(enabled: Boolean) {
        _autoDownloadEnabled.value = enabled
    }

    override fun cancel(episodeId: String) {
        _downloads.value = _downloads.value - episodeId
        onStatusChanged(DownloadStatus.Failed(episodeId, "Cancelled"))
    }

    override fun enqueue(episode: Episode, auto: Boolean) {
        if (_downloads.value[episode.id] is DownloadStatus.InProgress) return
        scope.launch {
            var progress = 0f
            while (progress < 1f) {
                progress = (progress + 0.2f).coerceAtMost(1f)
                val status = if (progress < 1f) {
                    DownloadStatus.InProgress(episode.id, progress)
                } else {
                    DownloadStatus.Completed(episode.id, "downloads/${episode.id}.mp3")
                }
                _downloads.value = _downloads.value + (episode.id to status)
                onStatusChanged(status)
                if (status is DownloadStatus.Completed) break
                delay(500)
            }
        }
    }

    override fun clearDownloads(episodeIds: Collection<String>) {
        if (episodeIds.isEmpty()) return
        _downloads.value = _downloads.value - episodeIds
    }
}
