package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mercury.sqkon.db.internal.ListenerIdentityMap
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonDriver
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.SqkonTransaction
import com.mercury.sqkon.db.internal.schema.SqkonSchema

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

internal fun SqkonSchema.toSqlDelightSchema(): SqlSchema<QueryResult.Value<Unit>> =
    object : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = this@toSqlDelightSchema.version

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            this@toSqlDelightSchema.create(SqlDelightSqkonDriver(driver))
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            this@toSqlDelightSchema.migrate(SqlDelightSqkonDriver(driver), oldVersion, newVersion)
            return QueryResult.Unit
        }
    }
