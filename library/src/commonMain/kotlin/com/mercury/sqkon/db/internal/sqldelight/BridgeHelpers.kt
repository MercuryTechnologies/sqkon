package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlPreparedStatement
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonStatement

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
