package com.opoojkk.podium.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = PodcastDatabase.Schema,
            name = "podium.db",
            onConfiguration = { config ->
                config.copy(
                    version = 5,
                    upgradeBlock = { driver, oldVersion, newVersion ->
                        if (oldVersion < 2 && newVersion >= 2) {
                            // Migration 1: Add durationMs column (if needed)
                            try {
                                driver.execute(null, "ALTER TABLE playback_state ADD COLUMN durationMs INTEGER", 0)
                            } catch (e: Exception) {
                                // Column might already exist
                            }
                        }
                        if (oldVersion < 3 && newVersion >= 3) {
                            // Migration 2: Add chapters column
                            driver.execute(null, "ALTER TABLE episodes ADD COLUMN chapters TEXT", 0)
                        }
                        if (oldVersion < 4 && newVersion >= 4) {
                            // Migration 3: Add app_settings table
                            driver.execute(null, """
                                CREATE TABLE IF NOT EXISTS app_settings (
                                    key TEXT NOT NULL PRIMARY KEY,
                                    value TEXT NOT NULL
                                )
                            """.trimIndent(), 0)
                        }
                        if (oldVersion < 5 && newVersion >= 5) {
                            // Migration 4: Add isCompleted and addedToPlaylist columns
                            try {
                                driver.execute(null, "ALTER TABLE playback_state ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0", 0)
                            } catch (e: Exception) {
                                // Column might already exist
                            }
                            try {
                                driver.execute(null, "ALTER TABLE playback_state ADD COLUMN addedToPlaylist INTEGER NOT NULL DEFAULT 1", 0)
                            } catch (e: Exception) {
                                // Column might already exist
                            }
                        }
                    }
                )
            }
        )
    }
}
