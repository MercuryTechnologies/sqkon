package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver

internal expect val connectionPoolSize: Int

@PublishedApi
internal expect val defaultSqkonDispatchers: SqkonDispatchers

internal expect class DriverFactory {
    fun createDriver(): SqlDriver
}
