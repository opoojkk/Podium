package com.opoojkk.podium.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver() = JdbcSqliteDriver("jdbc:sqlite:podium.db").also { driver ->
        runCatching { PodcastDatabase.Schema.create(driver) }
    }
}
