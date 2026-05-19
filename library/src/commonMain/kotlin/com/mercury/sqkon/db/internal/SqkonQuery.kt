package com.mercury.sqkon.db.internal

/**
 * Sqkon-owned, synchronous mirror of `app.cash.sqldelight.Query`.
 *
 * SQLDelight wraps return values in `QueryResult` so it can support an async-codegen
 * mode. Sqkon never uses async codegen (`generateAsync = false`), so we model
 * execution synchronously.
 */
internal abstract class SqkonQuery<out T : Any>(
    private val mapper: (SqkonCursor) -> T,
) {
    abstract fun addListener(listener: Listener)
    abstract fun removeListener(listener: Listener)

    abstract fun <R> execute(mapper: (SqkonCursor) -> R): R

    fun executeAsList(): List<T> = execute { cursor ->
        buildList { while (cursor.next()) add(mapper(cursor)) }
    }

    fun executeAsOne(): T = executeAsOneOrNull()
        ?: throw NullPointerException("ResultSet returned null for $this")

    fun executeAsOneOrNull(): T? = execute { cursor ->
        if (cursor.next()) mapper(cursor) else null
    }

    fun interface Listener {
        fun queryResultsChanged()
    }
}
