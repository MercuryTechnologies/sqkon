package com.mercury.sqkon

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Will iterate on the block until it returns true or the timeout is reached.
 *
 * @throws TimeoutCancellationException if the timeout is reached.
 */
@Throws(TimeoutCancellationException::class)
suspend fun until(timeout: Duration = 5.seconds, block: suspend () -> Boolean) {
    withTimeout(timeout) {
        while (!block()) {
            delay(100)
        }
    }
}