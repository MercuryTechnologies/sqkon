package com.mercury.sqkon.db

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers

internal actual val connectionPoolSize: Int = 1

@PublishedApi
// TODO(MOB-3293): single-thread write dispatcher
internal actual val defaultSqkonDispatchers: SqkonDispatchers = SqkonDispatchers(
    read = Dispatchers.Default,
    write = Dispatchers.Default,
)

internal actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        TODO("iOS driver not yet implemented — lands in Phase 6 (MOB-3293)")
}
