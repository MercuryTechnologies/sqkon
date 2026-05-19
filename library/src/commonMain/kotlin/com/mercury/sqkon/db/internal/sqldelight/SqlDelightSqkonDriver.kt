package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import com.mercury.sqkon.db.internal.ListenerIdentityMap
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction

internal class SqlDelightSqkonDriver(
    internal val delegate: SqlDriver,
) : SqkonDriver {

    private val listeners = ListenerIdentityMap<SqkonDriver.Listener, Query.Listener>(
        factory = { listener -> Query.Listener { listener.queryResultsChanged() } },
    )

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
        listeners.add(listener) { delegate.addListener(queryKeys = queryKeys, listener = it) }
    }

    override fun removeListener(vararg queryKeys: String, listener: SqkonDriver.Listener) {
        listeners.remove(listener) { delegate.removeListener(queryKeys = queryKeys, listener = it) }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        delegate.notifyListeners(queryKeys = queryKeys)
    }

    override fun newTransaction(): SqkonTransaction =
        SqlDelightSqkonTransaction(delegate.newTransaction().value)

    override fun currentTransaction(): SqkonTransaction? =
        delegate.currentTransaction()?.let(::SqlDelightSqkonTransaction)

    override fun close() = delegate.close()
}
