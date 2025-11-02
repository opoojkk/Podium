package com.opoojkk.podium.data.local

import com.opoojkk.podium.data.model.UpdateInterval
import com.opoojkk.podium.db.PodcastQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

/**
 * Manages app-wide settings stored in the database.
 */
class AppSettings(private val queries: PodcastQueries) {

    private val _updateInterval = MutableStateFlow(UpdateInterval.DAILY)
    val updateInterval: Flow<UpdateInterval> = _updateInterval.asStateFlow()

    private val _lastGlobalUpdate = MutableStateFlow<Instant?>(null)
    val lastGlobalUpdate: Flow<Instant?> = _lastGlobalUpdate.asStateFlow()

    companion object {
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        private const val KEY_LAST_GLOBAL_UPDATE = "last_global_update"
    }

    init {
        // Load settings from database
        loadSettings()
    }

    private fun loadSettings() {
        // Load update interval
        val intervalValue = queries.getSetting(KEY_UPDATE_INTERVAL).executeAsOneOrNull()
        if (intervalValue != null) {
            _updateInterval.value = try {
                UpdateInterval.valueOf(intervalValue)
            } catch (e: IllegalArgumentException) {
                UpdateInterval.DAILY
            }
        }

        // Load last global update time
        val lastUpdateValue = queries.getSetting(KEY_LAST_GLOBAL_UPDATE).executeAsOneOrNull()
        if (lastUpdateValue != null) {
            _lastGlobalUpdate.value = try {
                Instant.parse(lastUpdateValue)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Set the update interval preference.
     */
    suspend fun setUpdateInterval(interval: UpdateInterval) {
        queries.upsertSetting(KEY_UPDATE_INTERVAL, interval.name)
        _updateInterval.value = interval
    }

    /**
     * Get the current update interval preference.
     */
    fun getUpdateInterval(): UpdateInterval {
        return _updateInterval.value
    }

    /**
     * Update the last global update timestamp.
     */
    suspend fun updateLastGlobalUpdate(timestamp: Instant) {
        queries.upsertSetting(KEY_LAST_GLOBAL_UPDATE, timestamp.toString())
        _lastGlobalUpdate.value = timestamp
    }

    /**
     * Get the last global update timestamp.
     */
    fun getLastGlobalUpdate(): Instant? {
        return _lastGlobalUpdate.value
    }

    /**
     * Check if an update should be performed based on the current interval setting.
     */
    fun shouldUpdate(): Boolean {
        val interval = _updateInterval.value
        val lastUpdate = _lastGlobalUpdate.value

        return when (interval) {
            UpdateInterval.EVERY_TIME -> true
            UpdateInterval.DAILY -> {
                if (lastUpdate == null) {
                    true
                } else {
                    val now = kotlinx.datetime.Clock.System.now()
                    val dayInMillis = 24 * 60 * 60 * 1000L
                    (now.toEpochMilliseconds() - lastUpdate.toEpochMilliseconds()) >= dayInMillis
                }
            }
            UpdateInterval.MANUAL -> false
        }
    }
}
