package com.mercury.sqkon.db

import androidx.test.platform.app.InstrumentationRegistry

actual fun createEntityQueries(): EntityQueries {
    return createEntityQueries(
        DriverFactory(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            name = null // in-memory database
        )
    )
}