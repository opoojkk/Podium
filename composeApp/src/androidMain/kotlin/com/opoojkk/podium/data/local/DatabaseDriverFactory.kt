package com.opoojkk.podium.data.local

import android.content.Context
import android.util.Log
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
                AfterVersion(0) { driver ->
                    // Migration from version 0/1: Add both columns
                    Log.d("DBMigration", "Migrating from version 0: Adding durationMs and chapters")
                    try {
                        driver.execute(null, "ALTER TABLE playback_state ADD COLUMN durationMs INTEGER", 0)
                    } catch (e: Exception) {
                        Log.d("DBMigration", "durationMs exists: ${e.message}")
                    }
                    try {
                        driver.execute(null, "ALTER TABLE episodes ADD COLUMN chapters TEXT", 0)
                    } catch (e: Exception) {
                        Log.d("DBMigration", "chapters exists: ${e.message}")
                    }
                },
                AfterVersion(1) { driver ->
                    // Migration from version 1: Add both columns
                    Log.d("DBMigration", "Migrating from version 1: Adding durationMs and chapters")
                    try {
                        driver.execute(null, "ALTER TABLE playback_state ADD COLUMN durationMs INTEGER", 0)
                    } catch (e: Exception) {
                        Log.d("DBMigration", "durationMs exists: ${e.message}")
                    }
                    try {
                        driver.execute(null, "ALTER TABLE episodes ADD COLUMN chapters TEXT", 0)
                    } catch (e: Exception) {
                        Log.d("DBMigration", "chapters exists: ${e.message}")
                    }
                },
                AfterVersion(2) { driver ->
                    // Migration from version 2 to 3: Add chapters column
                    Log.d("DBMigration", "Migrating from version 2: Adding chapters column")
                    try {
                        driver.execute(null, "ALTER TABLE episodes ADD COLUMN chapters TEXT", 0)
                        Log.d("DBMigration", "✓ Added chapters column")
                    } catch (e: Exception) {
                        Log.e("DBMigration", "Failed: ${e.message}")
                    }
                }
            )
        )
    }
}
