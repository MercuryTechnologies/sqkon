package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import com.mercury.sqkon.db.internal.ListenerIdentityMap
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonQuery

/**
 * Adapts a SQLDelight `Query<T>` as a `SqkonQuery<T>`. Used for the still-generated
 * `MetadataQueries` until Phase 3 hand-rolls the data class and Phase 4 retires the
 * `.sq` files. Delete this when SQLDelight codegen is gone.
 *
 * All execute paths delegate straight to the underlying `Query<T>` — the `SqkonQuery`
 * mapper is never invoked, so the sentinel `error(...)` lambda is unreachable.
 */
internal fun <T : Any> Query<T>.toSqkonQuery(): SqkonQuery<T> {
    val delegate = this
    val listeners = ListenerIdentityMap<SqkonQuery.Listener, Query.Listener>(
        factory = { listener -> Query.Listener { listener.queryResultsChanged() } },
    )
    return object : SqkonQuery<T>({ _: SqkonCursor -> error("unreachable: delegate Query carries its own mapper") }) {
        override fun addListener(listener: Listener) = listeners.add(listener, delegate::addListener)
        override fun removeListener(listener: Listener) = listeners.remove(listener, delegate::removeListener)
        override fun <R> execute(mapper: (SqkonCursor) -> R): R =
            delegate.execute(mapWithSqkonCursor(mapper)).value
        override fun executeAsList(): List<T> = delegate.executeAsList()
        override fun executeAsOne(): T = delegate.executeAsOne()
        override fun executeAsOneOrNull(): T? = delegate.executeAsOneOrNull()
    }
}
