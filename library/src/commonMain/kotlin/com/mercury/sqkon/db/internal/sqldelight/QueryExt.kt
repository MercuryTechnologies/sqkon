package com.mercury.sqkon.db.internal.sqldelight

import app.cash.sqldelight.Query
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonQuery

/**
 * Adapt a SQLDelight `Query<T>` as a `SqkonQuery<T>`. Used for the still-generated
 * `MetadataQueries` until Phase 3 hand-rolls the data class.
 *
 * The mapper passed to `SqkonQuery` is unused — `SqlDelightSqkonQuery.execute`
 * delegates straight to the underlying `Query<T>`, which carries its own mapper.
 */
internal fun <T : Any> Query<T>.toSqkonQuery(): SqkonQuery<T> = SqlDelightSqkonQuery(
    delegate = this,
    mapper = { _: SqkonCursor -> error("Unused: SqlDelightSqkonQuery.execute delegates to underlying Query.execute") },
)
