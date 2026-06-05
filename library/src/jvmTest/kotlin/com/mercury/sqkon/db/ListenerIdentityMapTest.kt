package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.ListenerIdentityMap
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for the unsynchronized ListenerIdentityMap (#76).
 *
 * `DriverBackedSqkonQuery` owns one map per query, and `asFlow()` (callbackFlow) calls
 * `add` on collection start / `remove` in `awaitClose` on arbitrary dispatchers, so a cold Flow
 * collected by multiple coroutines mutates the shared map concurrently. With a plain
 * `mutableMapOf`, concurrent `getOrPut` for the same key races: the factory runs more than once
 * and the surplus delegate is registered with the driver but never stored, so it can never be
 * removed (a listener leak).
 */
class ListenerIdentityMapTest {

    @Test
    fun concurrentAdd_sameKey_runsFactoryExactlyOnce() {
        // Repeat to make the race reliably observable on the unsynchronized map.
        repeat(50) { round ->
            val factoryCalls = AtomicInteger(0)
            val map = ListenerIdentityMap<Int, Any>(factory = { factoryCalls.incrementAndGet(); Any() })
            val threadCount = 16
            val barrier = CyclicBarrier(threadCount)
            val registered = CopyOnWriteArrayList<Any>()
            val errors = CopyOnWriteArrayList<Throwable>()

            (0 until threadCount).map {
                thread {
                    try {
                        barrier.await() // all threads hit add() for the same key simultaneously
                        map.add(round) { delegate -> registered += delegate }
                    } catch (e: Throwable) {
                        errors += e
                    }
                }
            }.forEach { it.join() }

            // Worker threads swallow their own exceptions otherwise — surface them here.
            assertTrue(errors.isEmpty(), "worker threw in round $round: ${errors.firstOrNull()}")
            // Every worker must have run its register callback.
            assertEquals(threadCount, registered.size, "not every worker registered in round $round")
            // The map's getOrPut must run the factory exactly once per key, even under a
            // concurrent storm — otherwise surplus delegates are registered but never tracked.
            assertEquals(1, factoryCalls.get(), "factory ran ${factoryCalls.get()}× in round $round")
            // Every add registers the single shared delegate.
            assertTrue(registered.all { it === registered.first() }, "registered distinct delegates")
        }
    }
}
