package com.mercury.sqkon.db

import androidx.test.platform.app.InstrumentationRegistry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal actual fun driverFactory(): DriverFactory {
    val uuid = Uuid.random()
    return DriverFactory(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        // random file each time to make sure testing against file system and WAL is enabled
        name = "sqkon-test-$uuid.db",
    )
}
