package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonDriver

internal expect val connectionPoolSize: Int

@PublishedApi
internal expect val defaultSqkonDispatchers: SqkonDispatchers

internal expect class DriverFactory {
    fun createDriver(): SqkonDriver
}
