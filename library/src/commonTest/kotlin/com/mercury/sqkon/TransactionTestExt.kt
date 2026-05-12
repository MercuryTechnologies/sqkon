package com.mercury.sqkon

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Verify commit-visibility ordering:
 *  1. [flow] emits [initial] before any write.
 *  2. During [commit] (a slow write), [reader] still observes [initial].
 *  3. After [commit] returns, [flow] emits a value matching [matchPost] and a follow-up
 *     [reader] call observes the same.
 *
 * Used to pin the "reader sees pre-commit snapshot until commit" SQLite WAL behavior.
 */
suspend fun <T> assertVisibilityOrder(
    flow: Flow<T>,
    initial: T,
    commit: suspend () -> Unit,
    reader: suspend () -> T,
    matchPost: (T) -> Boolean,
) = coroutineScope {
    flow.test(timeout = 10.seconds) {
        assertEquals(initial, awaitItem())
        val midRead = async(Dispatchers.Default) {
            delay(20.milliseconds) // let commit() start
            reader()
        }
        commit()
        // mid-commit reader must have seen the pre-commit snapshot
        assertEquals(initial, midRead.await())
        // post-commit emission
        val post = awaitItem()
        assertTrue(matchPost(post), "post-commit flow emission did not match: $post")
        // post-commit reader sees the new value
        assertTrue(matchPost(reader()), "post-commit reader did not match")
        cancelAndIgnoreRemainingEvents()
    }
}
