package com.mercury.sqkon.db

import com.mercury.sqkon.db.internal.SqkonTransacter
import com.mercury.sqkon.db.internal.SqkonTransaction

/**
 * Run [body] in a database transaction. Calling [SqkonTransactionScope.rollback] discards the work
 * and returns silently.
 */
fun <T : Any> KeyValueStorage<T>.transaction(body: SqkonTransactionScope.() -> Unit): Unit =
    runTransaction(body)

/**
 * Run [body] in a database transaction and return its value. Calling [SqkonTransactionScope.rollback]
 * aborts the transaction and throws [SqkonRollbackException] (there is no value to return).
 */
fun <T : Any, R> KeyValueStorage<T>.transactionWithResult(body: SqkonTransactionScope.() -> R): R =
    runTransactionWithResult(body)

/** Sealed-impl in the same package as [SqkonTransactionScope]. Built by [SqkonTransacter]. */
internal class ScopeReceiver(
    private val transaction: SqkonTransaction,
    private val transacter: SqkonTransacter,
) : SqkonTransactionScope {
    override fun afterCommit(action: () -> Unit) = transaction.afterCommit(action)
    override fun afterRollback(action: () -> Unit) = transaction.afterRollback(action)
    override fun rollback(): Nothing = throw SqkonRollbackException()
    override fun transaction(body: SqkonTransactionScope.() -> Unit) {
        transacter.transaction(noEnclosing = false, body = body)
    }
}

internal fun newScopeReceiver(transaction: SqkonTransaction, transacter: SqkonTransacter): SqkonTransactionScope =
    ScopeReceiver(transaction, transacter)
