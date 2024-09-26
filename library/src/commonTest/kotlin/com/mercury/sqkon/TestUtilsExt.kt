package com.mercury.sqkon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val timeoutDispatcher = Dispatchers.Default.limitedParallelism(1, "timeout")

/**
 * Will iterate on the block until it returns true or the timeout is reached.
 *
 * @throws TimeoutCancellationException if the timeout is reached.
 */
@Throws(TimeoutCancellationException::class)
suspend fun until(timeout: Duration = 5.seconds, block: suspend () -> Boolean) =
    withContext(timeoutDispatcher) {
        withTimeout(timeout) {
            while (!block()) {
                delay(100)
            }
        }
    }