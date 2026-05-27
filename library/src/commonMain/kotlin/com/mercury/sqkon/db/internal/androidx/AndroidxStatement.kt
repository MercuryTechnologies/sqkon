// Adapted from com.eygraber:sqldelight-androidx-driver 0.0.17 (Apache-2.0).
package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteStatement
import com.mercury.sqkon.db.internal.SqkonCursor
import com.mercury.sqkon.db.internal.SqkonStatement

/**
 * A prepared/cached `androidx.sqlite.SQLiteStatement` wrapped as a Sqkon binder. Cached statements
 * are reset between uses by [com.mercury.sqkon.db.internal.androidx.SqkonStatementCache].
 *
 * `androidx.sqlite` parameter indices are 1-based; Sqkon/SQLDelight binders are 0-based — hence
 * `index + 1` everywhere.
 */
internal sealed class AndroidxStatement(
    protected val statement: SQLiteStatement,
) : SqkonStatement {
    override fun bindBytes(index: Int, value: ByteArray?) {
        if (value == null) statement.bindNull(index + 1) else statement.bindBlob(index + 1, value)
    }
    override fun bindBoolean(index: Int, value: Boolean?) {
        if (value == null) statement.bindNull(index + 1)
        else statement.bindLong(index + 1, if (value) 1L else 0L)
    }
    override fun bindDouble(index: Int, value: Double?) {
        if (value == null) statement.bindNull(index + 1) else statement.bindDouble(index + 1, value)
    }
    override fun bindLong(index: Int, value: Long?) {
        if (value == null) statement.bindNull(index + 1) else statement.bindLong(index + 1, value)
    }
    override fun bindString(index: Int, value: String?) {
        if (value == null) statement.bindNull(index + 1) else statement.bindText(index + 1, value)
    }

    fun reset() = statement.reset()
    fun close() = statement.close()
}

/** DML/DDL — no rows. Caller invokes [execute] after binding. */
internal class AndroidxPreparedStatement(statement: SQLiteStatement) : AndroidxStatement(statement) {
    /** Step the statement to completion. */
    fun execute() { while (statement.step()) { /* drain */ } }
}

/** SELECT — caller invokes [executeQuery] after binding to map the cursor. */
internal class AndroidxQuery(statement: SQLiteStatement) : AndroidxStatement(statement) {
    fun <R> executeQuery(mapper: (SqkonCursor) -> R): R = mapper(AndroidxSqkonCursor(statement))
}
