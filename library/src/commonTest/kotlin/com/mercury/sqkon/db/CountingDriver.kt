package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction

/**
 * Delegating [SqkonDriver] that records the SQL of every `executeQuery` for assertions.
 *
 * Shared by the paging performance guards ([KeysetPagingPerformanceTest],
 * [OffsetPagingPerformanceTest]) to count actual SQL executions deterministically — no timing,
 * no flakiness. Wrap a real driver: `CountingDriver(driverFactory().createDriver())`.
 */
internal class CountingDriver(private val delegate: SqkonDriver) : SqkonDriver {
    val queries = mutableListOf<String>()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
        mapper: (SqkonCursor) -> R,
    ): R {
        queries += sql
        return delegate.executeQuery(identifier, sql, parameters, binders, mapper)
    }

    override fun executeUpdate(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
    ): Long = delegate.executeUpdate(identifier, sql, parameters, binders)

    override fun addListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        delegate.addListener(queryKeys = queryKeys, listener = listener)

    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) =
        delegate.removeListener(queryKeys = queryKeys, listener = listener)

    override fun notifyListeners(vararg queryKeys: String) =
        delegate.notifyListeners(queryKeys = queryKeys)

    override fun newTransaction(): SqkonTransaction = delegate.newTransaction()
    override fun currentTransaction(): SqkonTransaction? = delegate.currentTransaction()
    override fun close() = delegate.close()

    fun countMatching(fragment: String): Int = queries.count { it.contains(fragment) }
}
