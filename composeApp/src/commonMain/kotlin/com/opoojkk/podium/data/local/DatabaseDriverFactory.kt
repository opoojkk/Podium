package com.opoojkk.podium.data.local

import app.cash.sqldelight.db.SqlDriver
import com.opoojkk.podium.platform.PlatformContext

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

