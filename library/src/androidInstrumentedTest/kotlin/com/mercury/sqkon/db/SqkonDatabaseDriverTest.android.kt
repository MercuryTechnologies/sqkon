package com.mercury.sqkon.db

import androidx.test.platform.app.InstrumentationRegistry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal actual fun driverFactory(): DriverFactory {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val uuid = Uuid.random()
    // random file each time so each test gets a fresh WAL-enabled file-backed DB
    return DriverFactory(
        context = ctx,
        type = SqkonDatabaseType.FileBacked(ctx.getDatabasePath("sqkon-test-$uuid.db").absolutePath),
    )
}
