package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonQuery

internal class SqlDelightSqkonQuery<T : Any>(
    private val delegate: Query<T>,
    mapper: (SqkonCursor) -> T,
) : SqkonQuery<T>(mapper) {

    // Same SqkonQuery.Listener must map to the same Query.Listener so removeListener
    // can find the exact instance the delegate registered.
    private val listeners = mutableMapOf<Listener, Query.Listener>()

    override fun addListener(listener: Listener) {
        val delegateListener = listeners.getOrPut(listener) {
            Query.Listener { listener.queryResultsChanged() }
        }
        delegate.addListener(delegateListener)
    }

    override fun removeListener(listener: Listener) {
        val delegateListener = listeners.remove(listener) ?: return
        delegate.removeListener(delegateListener)
    }

    override fun <R> execute(mapper: (SqkonCursor) -> R): R =
        delegate.execute(mapWithSqkonCursor(mapper)).value

    // Skip the SqkonQuery mapper field when we have a fully-formed delegate Query<T>
    // (the delegate already carries its own row mapper). Bridge users go through
    // toSqkonQuery() which passes a sentinel mapper.
    override fun executeAsList(): List<T> = delegate.executeAsList()
    override fun executeAsOne(): T = delegate.executeAsOne()
    override fun executeAsOneOrNull(): T? = delegate.executeAsOneOrNull()
}
