package com.mercury.sqkon.db

import androidx.test.platform.app.InstrumentationRegistry

internal actual fun driverFactory(): DriverFactory {
    return DriverFactory(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        name = null // in-memory database
    )
}
