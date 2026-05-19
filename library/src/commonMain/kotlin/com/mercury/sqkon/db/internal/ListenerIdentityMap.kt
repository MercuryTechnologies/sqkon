package com.mercury.sqkon.db.internal

/**
 * Maintains an identity map between a Sqkon listener and a delegate listener so
 * `removeListener` can find the same delegate instance the underlying driver
 * registered. Used by the three places that bridge SQLDelight listeners to
 * Sqkon listeners (driver, query, EntityQueries inner classes).
 *
 * Not thread-safe — listener registration is expected single-threaded per query
 * (mirrors SQLDelight's `Query` contract).
 */
internal class ListenerIdentityMap<K : Any, V : Any>(
    private val factory: (K) -> V,
) {
    private val map = mutableMapOf<K, V>()

    fun add(key: K, register: (V) -> Unit) {
        val delegate = map.getOrPut(key) { factory(key) }
        register(delegate)
    }

    fun remove(key: K, unregister: (V) -> Unit) {
        val delegate = map.remove(key) ?: return
        unregister(delegate)
    }
}
