package com.mercury.sqkon

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.delay
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Assert no item is emitted within [window]. Use to prove that a write to one store
 * does NOT trigger a Flow over a different store. Drains anything already queued first.
 */
suspend fun <T> ReceiveTurbine<T>.expectNoEventsBriefly(
    window: Duration = 200.milliseconds,
) {
    delay(window)
    expectNoEvents()
}

/**
 * Poll [awaitItem] up to [timeout] until [predicate] matches; fail otherwise.
 */
suspend fun <T> ReceiveTurbine<T>.awaitItemMatching(
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean,
): T {
    val deadline = kotlin.time.TimeSource.Monotonic.markNow() + timeout
    while (deadline.hasNotPassedNow()) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    fail("No item matched within $timeout")
}
