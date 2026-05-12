package com.mercury.sqkon

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wait [window] of real wall-clock time, then assert that no item was emitted. Use
 * to prove that a write to one store does NOT trigger a Flow over a different store.
 *
 * Does NOT pre-drain queued events: if the turbine already has buffered items when
 * called, [expectNoEvents] will fail. Callers must consume any expected emissions
 * (e.g. the initial `emptyList`) via `awaitItem()` before invoking this helper.
 *
 * The delay runs on [Dispatchers.Default] so virtual-time test schedulers cannot
 * short-circuit the wall-clock window.
 */
suspend fun <T> ReceiveTurbine<T>.expectNoEventsBriefly(
    window: Duration = 200.milliseconds,
) {
    withContext(Dispatchers.Default) {
        delay(window)
    }
    expectNoEvents()
}

/**
 * Poll [awaitItem] up to [timeout] until [predicate] matches; fail otherwise.
 *
 * Each iteration is bounded by the remaining time via [withTimeoutOrNull] so the
 * helper's [timeout] parameter actually caps wall-clock wait rather than being
 * dominated by Turbine's own global timeout.
 */
suspend fun <T> ReceiveTurbine<T>.awaitItemMatching(
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean,
): T {
    val start = kotlin.time.TimeSource.Monotonic.markNow()
    while (true) {
        val elapsed = start.elapsedNow()
        if (elapsed >= timeout) fail("No item matched within $timeout")
        val remaining = timeout - elapsed
        val item = withTimeoutOrNull(remaining) { awaitItem() }
            ?: fail("No item matched within $timeout (awaitItem timed out)")
        if (predicate(item)) return item
    }
}
