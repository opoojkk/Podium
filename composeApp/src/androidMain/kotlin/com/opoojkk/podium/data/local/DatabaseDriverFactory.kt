package com.opoojkk.podium.data.local

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = PodcastDatabase.Schema,
            context = context,
            name = "podium.db",
            callback = AndroidSqliteDriver.Callback(
                schema = PodcastDatabase.Schema,
                AfterVersion(1) { driver ->
                    // Migration from version 1 to 2: Add durationMs column
                    try {
                        driver.execute(null, "ALTER TABLE playback_state ADD COLUMN durationMs INTEGER", 0)
                    } catch (e: Exception) {
                        // Column might already exist, ignore
                    }
                },
                AfterVersion(2) { driver ->
                    // Migration from version 2 to 3: Add chapters column
                    driver.execute(null, "ALTER TABLE episodes ADD COLUMN chapters TEXT", 0)
                }
            )
        )
    }
}
