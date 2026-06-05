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
        // The iOS target is a compile-only scaffold: it publishes a klib but has no working driver
        // (and no public Sqkon() entry point) yet. Fail fast and explicitly rather than appear
        // usable. iOS runtime support is on the roadmap — see the README "iOS status" note.
        throw NotImplementedError(
            "Sqkon does not support iOS at runtime yet — the iOS target is a compile-only scaffold. " +
                "Use the Android or JVM targets; iOS support is on the roadmap.",
        )
}
