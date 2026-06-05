package com.mercury.sqkon.db.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Maintains an identity map between a Sqkon listener and a delegate listener so
 * `removeListener` can find the same delegate instance the underlying driver
 * registered. Used by `DriverBackedSqkonQuery` to bridge `SqkonQuery.Listener`s to
 * `SqkonDriver.Listener`s.
 *
 * Thread-safe: `asFlow()` (callbackFlow) calls [add] on collection start and [remove] in
 * `awaitClose` on arbitrary dispatchers, so a cold Flow collected by multiple coroutines mutates
 * one map concurrently. The map mutation is guarded by a [Mutex]; the register/unregister callback
 * runs outside the lock (it calls into the driver and must not re-enter the lock). See #76.
 */
internal class ListenerIdentityMap<K : Any, V : Any>(
    private val factory: (K) -> V,
) {
    private val map = mutableMapOf<K, V>()
    private val mutex = Mutex()

    fun add(key: K, register: (V) -> Unit) {
        val delegate = sqkonRunBlocking { mutex.withLock { map.getOrPut(key) { factory(key) } } }
        register(delegate)
    }

    fun remove(key: K, unregister: (V) -> Unit) {
        val delegate = sqkonRunBlocking { mutex.withLock { map.remove(key) } } ?: return
        unregister(delegate)
    }
}
