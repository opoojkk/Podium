package com.opoojkk.podium.data.local

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = run {
        val databasePath = "podium.db"
        val databaseFileExisted = java.io.File(databasePath).exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

        if (!databaseFileExisted) {
            // 新数据库：直接创建所有表
            PodcastDatabase.Schema.create(driver)
        } else {
            // 已存在的数据库：执行迁移
            migrateIfNeeded(driver)
        }

        driver
    }

    private fun migrateIfNeeded(driver: SqlDriver) {
        val currentVersion = getCurrentVersion(driver)
        val targetVersion = 3 // 当前版本号

        if (currentVersion < targetVersion) {
            println("🔄 Migrating database from version $currentVersion to $targetVersion")

            // 执行迁移
            for (version in currentVersion until targetVersion) {
                when (version) {
                    0 -> migrateToV1(driver)
                    1 -> migrateToV2(driver)
                    2 -> migrateToV3(driver)
                }
            }

            // 更新版本号
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

    // 迁移到版本1（如果之前没有版本控制）
    private fun migrateToV1(driver: SqlDriver) {
        println("  → Migrating to V1: Creating initial schema...")
        // 如果表不存在，创建它们
        try {
            PodcastDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // 表可能已存在，继续
            println("  → Tables already exist, continuing...")
        }
    }

    // 迁移到版本2：添加 playback_state.durationMs 字段
    private fun migrateToV2(driver: SqlDriver) {
        println("  → Migrating to V2: Adding durationMs to playback_state...")

        try {
            // 检查字段是否已存在
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
                // 添加 durationMs 字段
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

    // 迁移到版本3：添加 episodes.chapters 字段
    private fun migrateToV3(driver: SqlDriver) {
        println("  → Migrating to V3: Adding chapters to episodes...")

        try {
            // 检查字段是否已存在
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
                // 添加 chapters 字段
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
}
