package com.opoojkk.podium.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver() = NativeSqliteDriver(PodcastDatabase.Schema, "podium.db")
}
