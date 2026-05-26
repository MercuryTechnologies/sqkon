package com.mercury.sqkon.db

import app.cash.sqldelight.TransactionCallbacks
import com.mercury.sqkon.db.internal.SqkonTransacter

/**
 * Run [body] in a database transaction. Replaces the SQLDelight `Transacter.transaction { }` that
 * [KeyValueStorage] used to inherit. Calling [SqkonTransactionScope.rollback] discards the work and
 * returns silently.
 */
fun <T : Any> KeyValueStorage<T>.transaction(body: SqkonTransactionScope.() -> Unit): Unit =
    runTransaction(body)

/**
 * Run [body] in a database transaction and return its value. Calling [SqkonTransactionScope.rollback]
 * aborts the transaction and throws [SqkonRollbackException] (there is no value to return).
 */
fun <T : Any, R> KeyValueStorage<T>.transactionWithResult(body: SqkonTransactionScope.() -> R): R =
    runTransactionWithResult(body)

/**
 * Adapts a SQLDelight transaction body to [SqkonTransactionScope]. [rollback] always throws
 * [SqkonRollbackException]; the `Unit` [transaction] entry point swallows it (silent abort) while
 * [transactionWithResult] lets it propagate. The thrown exception escaping the body is what drives
 * SQLDelight to roll the (possibly enclosing) transaction back.
 */
internal class SqlDelightTransactionScope(
    private val callbacks: TransactionCallbacks,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = callbacks.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = callbacks.afterRollback(action)
    override fun rollback(): Nothing = throw SqkonRollbackException()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        // Route nesting through SqkonTransacter so trxMap records the parent link.
        transacter.transaction(noEnclosing = false) {
            SqlDelightTransactionScope(this, transacter).body()
        }
    }
}
