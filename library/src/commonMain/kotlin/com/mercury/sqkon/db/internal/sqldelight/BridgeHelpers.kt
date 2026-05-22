package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonStatement
import com.mercury.sqkon.db.internal.schema.SqkonSchema

/** Wrap a `(SqkonCursor) -> R` mapper as the `(SqlCursor) -> QueryResult<R>` SQLDelight expects. */
internal fun <R> mapWithSqkonCursor(
    mapper: (SqkonCursor) -> R,
): (SqlCursor) -> QueryResult<R> = { sqlCursor ->
    QueryResult.Value(mapper(SqlDelightSqkonCursor(sqlCursor)))
}

/** Wrap a `(SqkonStatement.() -> Unit)?` binders lambda as the SQLDelight equivalent. */
internal fun wrapSqkonBinders(
    binders: (SqkonStatement.() -> Unit)?,
): (SqlPreparedStatement.() -> Unit)? =
    binders?.let { b -> { SqlDelightSqkonStatement(this).b() } }

/**
 * Adapt a [SqkonSchema] to the `SqlSchema<QueryResult.Value<Unit>>` that `AndroidxSqliteDriver`
 * still requires. Removed in Phase 6 once the driver no longer depends on SQLDelight types.
 */
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
            check(callbacks.isEmpty()) {
                "AfterVersion callbacks are not supported by the SqkonSchema bridge"
            }
            this@toSqlDelightSchema.migrate(SqlDelightSqkonDriver(driver), oldVersion, newVersion)
            return QueryResult.Unit
        }
    }
