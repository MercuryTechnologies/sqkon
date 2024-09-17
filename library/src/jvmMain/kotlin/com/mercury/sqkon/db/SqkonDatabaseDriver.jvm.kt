package com.mercury.sqkon.db

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking

internal actual class DriverFactory {

    actual fun createDriver(): SqlDriver {
        return runBlocking { createDriverAwait() }
    }

    suspend fun createDriverAwait(): SqlDriver {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SqkonDatabase.Schema.awaitCreate(driver)
        return driver
    }

}
