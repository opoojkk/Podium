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
                    version = 3,
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
                    }
                )
            }
        )
    }
}
