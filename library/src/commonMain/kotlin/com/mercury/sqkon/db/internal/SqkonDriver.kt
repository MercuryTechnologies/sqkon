package com.mercury.sqkon.db.internal

/**
 * Sqkon-owned database driver contract: a small synchronous SQLite driver interface
 * (originally modeled on the subset of `app.cash.sqldelight.db.SqlDriver` Sqkon used).
 * The production implementation is `AndroidxSqkonDriver` over `androidx.sqlite`.
 */
internal interface SqkonDriver {

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
