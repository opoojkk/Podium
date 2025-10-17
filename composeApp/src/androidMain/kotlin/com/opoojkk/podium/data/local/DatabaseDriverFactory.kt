package com.opoojkk.podium.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.opoojkk.podium.db.PodcastDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver() = AndroidSqliteDriver(PodcastDatabase.Schema, context, "podium.db")
}
