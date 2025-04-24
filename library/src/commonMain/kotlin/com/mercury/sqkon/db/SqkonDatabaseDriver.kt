package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher

internal expect val connectionPoolSize: Int
@PublishedApi
internal expect val dbWriteDispatcher: CoroutineDispatcher
@PublishedApi
internal expect val dbReadDispatcher: CoroutineDispatcher

internal expect class DriverFactory {
    fun createDriver(): SqlDriver
}
