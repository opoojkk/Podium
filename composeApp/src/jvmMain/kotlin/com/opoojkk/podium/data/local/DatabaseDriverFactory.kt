package com.opoojkk.podium.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = run {
        val databasePath = "podium.db"
        val databaseFileExisted = java.io.File(databasePath).exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
        if (!databaseFileExisted) {
            PodcastDatabase.Schema.create(driver)
        }
        driver
    }
}
