package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonDriver
import kotlinx.coroutines.Dispatchers

internal actual val connectionPoolSize: Int = 1

@PublishedApi
internal actual val defaultSqkonDispatchers: SqkonDispatchers by lazy {
    SqkonDispatchers(
        read = Dispatchers.Default,
        write = Dispatchers.Default.limitedParallelism(1),
    )
}

internal actual class DriverFactory {
    actual fun createDriver(): SqkonDriver =
        TODO("iOS driver not yet implemented (BundledSQLiteDriver available; wiring is post-Phase-6)")
}
