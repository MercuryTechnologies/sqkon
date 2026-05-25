package com.mercury.sqkon.db

import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import com.mercury.sqkon.db.internal.SqkonTransacter

/**
 * Run [body] in a database transaction. Replaces the SQLDelight `Transacter.transaction { }` that
 * [KeyValueStorage] used to inherit. Calling [SqkonTransactionScope.rollback] discards the work and
 * returns silently.
 */
fun <T : Any> KeyValueStorage<T>.transaction(body: SqkonTransactionScope.() -> Unit): Unit =
    transaction(body) // resolves to the internal member (members win over extensions)

/**
 * Run [body] in a database transaction and return its value. Calling [SqkonTransactionScope.rollback]
 * aborts the transaction and throws [SqkonRollbackException] (there is no value to return).
 */
fun <T : Any, R> KeyValueStorage<T>.transactionWithResult(body: SqkonTransactionScope.() -> R): R =
    transactionWithResult(body) // resolves to the internal member

/** Scope backing the `Unit` transaction path. Wraps SQLDelight's [TransactionWithoutReturn]. */
internal class SqlDelightTransactionScope(
    private val tx: TransactionWithoutReturn,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = tx.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = tx.afterRollback(action)
    override fun rollback(): Nothing = tx.rollback()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        // Route nesting through SqkonTransacter so trxMap records the parent link.
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }
}

/** Scope backing the result path. Wraps SQLDelight's [TransactionWithReturn]; rollback throws. */
internal class SqlDelightResultTransactionScope<R>(
    @Suppress("unused") private val tx: TransactionWithReturn<R>,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = tx.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = tx.afterRollback(action)
    override fun rollback(): Nothing = throw SqkonRollbackException()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }
}
