package com.mercury.sqkon.db.internal

/**
 * Sqkon-owned database driver contract. Phase 2 mirrors the subset of
 * `app.cash.sqldelight.db.SqlDriver` that Sqkon actually uses. The
 * SQLDelight runtime sits behind `SqlDelightSqkonDriver` until Phase 6.
 */
interface SqkonDriver {

    fun executeUpdate(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)? = null,
    ): Long

    fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqkonStatement.() -> Unit)? = null,
        mapper: (SqkonCursor) -> R,
    ): R

    fun addListener(vararg queryKeys: String, listener: Listener)
    fun removeListener(vararg queryKeys: String, listener: Listener)
    fun notifyListeners(vararg queryKeys: String)

    fun newTransaction(): SqkonTransaction
    fun currentTransaction(): SqkonTransaction?

    fun close()

    fun interface Listener {
        fun queryResultsChanged()
    }
}
