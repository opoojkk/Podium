package com.opoojkk.podium.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(
            schema = PodcastDatabase.Schema,
            name = "podium.db"
        )

        // Perform migrations if needed
        migrateIfNeeded(driver)

        return driver
    }

    private fun migrateIfNeeded(driver: SqlDriver) {
        val currentVersion = getCurrentVersion(driver)
        val targetVersion = 5 // Current version number

        if (currentVersion < targetVersion) {
            println("🔄 Migrating database from version $currentVersion to $targetVersion")

            // Execute migrations
            for (version in currentVersion until targetVersion) {
                when (version) {
                    0 -> migrateToV1(driver)
                    1 -> migrateToV2(driver)
                    2 -> migrateToV3(driver)
                    3 -> migrateToV4(driver)
                    4 -> migrateToV5(driver)
                }
            }

            // Update version number
            setVersion(driver, targetVersion)
            println("✅ Migration completed to version $targetVersion")
        }
    }

    private fun getCurrentVersion(driver: SqlDriver): Int {
        return try {
            driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    QueryResult.Value(cursor.getLong(0)?.toInt() ?: 0)
                },
                parameters = 0,
                binders = null
            ).value ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun setVersion(driver: SqlDriver, version: Int) {
        driver.execute(
            identifier = null,
            sql = "PRAGMA user_version = $version",
            parameters = 0,
            binders = null
        )
    }

    // Migration to version 1 (if no previous version control)
    private fun migrateToV1(driver: SqlDriver) {
        println("  → Migrating to V1: Creating initial schema...")
        try {
            PodcastDatabase.Schema.create(driver)
        } catch (e: Exception) {
            println("  → Tables already exist, continuing...")
        }
    }

    // Migration to version 2: Add playback_state.durationMs field
    private fun migrateToV2(driver: SqlDriver) {
        println("  → Migrating to V2: Adding durationMs to playback_state...")

        try {
            val hasColumn = driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM pragma_table_info('playback_state') WHERE name='durationMs'",
                mapper = { cursor ->
                    val count = cursor.getLong(0)?.toInt() ?: 0
                    QueryResult.Value(count > 0)
                },
                parameters = 0,
                binders = null
            ).value ?: false

            if (!hasColumn) {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE playback_state ADD COLUMN durationMs INTEGER",
                    parameters = 0,
                    binders = null
                )
                println("  ✓ Added durationMs column to playback_state")
            } else {
                println("  ✓ durationMs column already exists")
            }
        } catch (e: Exception) {
            println("  ✗ Error migrating to V2: ${e.message}")
            throw e
        }
    }

    // Migration to version 3: Add episodes.chapters field
    private fun migrateToV3(driver: SqlDriver) {
        println("  → Migrating to V3: Adding chapters to episodes...")

        try {
            val hasColumn = driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM pragma_table_info('episodes') WHERE name='chapters'",
                mapper = { cursor ->
                    val count = cursor.getLong(0)?.toInt() ?: 0
                    QueryResult.Value(count > 0)
                },
                parameters = 0,
                binders = null
            ).value ?: false

            if (!hasColumn) {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE episodes ADD COLUMN chapters TEXT",
                    parameters = 0,
                    binders = null
                )
                println("  ✓ Added chapters column to episodes")
            } else {
                println("  ✓ chapters column already exists")
            }
        } catch (e: Exception) {
            println("  ✗ Error migrating to V3: ${e.message}")
            throw e
        }
    }

    // Migration to version 4: Add app_settings table
    private fun migrateToV4(driver: SqlDriver) {
        println("  → Migrating to V4: Adding app_settings table...")

        try {
            val tableExists = driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='app_settings'",
                mapper = { cursor ->
                    val count = cursor.getLong(0)?.toInt() ?: 0
                    QueryResult.Value(count > 0)
                },
                parameters = 0,
                binders = null
            ).value ?: false

            if (!tableExists) {
                driver.execute(
                    identifier = null,
                    sql = """
                        CREATE TABLE app_settings (
                            key TEXT NOT NULL PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                    """.trimIndent(),
                    parameters = 0,
                    binders = null
                )
                println("  ✓ Created app_settings table")
            } else {
                println("  ✓ app_settings table already exists")
            }
        } catch (e: Exception) {
            println("  ✗ Error migrating to V4: ${e.message}")
            throw e
        }
    }

    // Migration to version 5: Add playback_state.isCompleted and addedToPlaylist fields
    private fun migrateToV5(driver: SqlDriver) {
        println("  → Migrating to V5: Adding isCompleted and addedToPlaylist to playback_state...")

        try {
            val hasIsCompleted = driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM pragma_table_info('playback_state') WHERE name='isCompleted'",
                mapper = { cursor ->
                    val count = cursor.getLong(0)?.toInt() ?: 0
                    QueryResult.Value(count > 0)
                },
                parameters = 0,
                binders = null
            ).value ?: false

            if (!hasIsCompleted) {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE playback_state ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0",
                    parameters = 0,
                    binders = null
                )
                println("  ✓ Added isCompleted column to playback_state")
            } else {
                println("  ✓ isCompleted column already exists")
            }

            val hasAddedToPlaylist = driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM pragma_table_info('playback_state') WHERE name='addedToPlaylist'",
                mapper = { cursor ->
                    val count = cursor.getLong(0)?.toInt() ?: 0
                    QueryResult.Value(count > 0)
                },
                parameters = 0,
                binders = null
            ).value ?: false

            if (!hasAddedToPlaylist) {
                driver.execute(
                    identifier = null,
                    sql = "ALTER TABLE playback_state ADD COLUMN addedToPlaylist INTEGER NOT NULL DEFAULT 1",
                    parameters = 0,
                    binders = null
                )
                println("  ✓ Added addedToPlaylist column to playback_state")
            } else {
                println("  ✓ addedToPlaylist column already exists")
            }
        } catch (e: Exception) {
            println("  ✗ Error migrating to V5: ${e.message}")
            throw e
        }
    }
}
