package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction

internal class SqlDelightSqkonDriver(
    internal val delegate: SqlDriver,
) : SqkonDriver {

    // Same SqkonDriver.Listener must map to the same Query.Listener so
    // removeListener can find the exact instance the eygraber driver registered.
    private val listeners = mutableMapOf<SqkonDriver.Listener, Query.Listener>()

    override fun executeUpdate(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
    ): Long = delegate.execute(
        identifier = identifier,
        sql = sql,
        parameters = parameters,
        binders = wrapSqkonBinders(binders),
    ).value

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)?,
        mapper: (SqkonCursor) -> R,
    ): R = delegate.executeQuery(
        identifier = identifier,
        sql = sql,
        parameters = parameters,
        binders = wrapSqkonBinders(binders),
        mapper = mapWithSqkonCursor(mapper),
    ).value

    override fun addListener(vararg queryKeys: String, listener: SqkonDriver.Listener) {
        val delegateListener = listeners.getOrPut(listener) {
            Query.Listener { listener.queryResultsChanged() }
        }
        delegate.addListener(*queryKeys, listener = delegateListener)
    }

    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) {
        val delegateListener = listeners.remove(listener) ?: return
        delegate.removeListener(*queryKeys, listener = delegateListener)
    }

    override fun notifyListeners(vararg queryKeys: String) {
        delegate.notifyListeners(*queryKeys)
    }

    override fun newTransaction(): SqkonTransaction =
        SqlDelightSqkonTransaction(delegate.newTransaction().value)

    override fun currentTransaction(): SqkonTransaction? =
        delegate.currentTransaction()?.let(::SqlDelightSqkonTransaction)

    override fun close() = delegate.close()
}
