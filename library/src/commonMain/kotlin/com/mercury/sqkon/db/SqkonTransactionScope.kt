package com.mercury.sqkon.db

/**
 * Sqkon-owned transaction scope, the receiver of [transaction] / [transactionWithResult].
 *
 * Replaces direct exposure of SQLDelight's `Transacter` / `TransactionCallbacks`. `sealed` — only
 * Sqkon provides implementations; callers must not implement it.
 */
sealed interface SqkonTransactionScope {

    /** Run [action] after the outermost enclosing transaction commits. */
    fun afterCommit(action: () -> Unit)

    /** Run [action] after the transaction rolls back. */
    fun afterRollback(action: () -> Unit)

    /**
     * Abort the transaction by throwing [SqkonRollbackException], rolling back the database
     * transaction (and any enclosing one — there are no savepoints).
     *
     * [transaction] swallows the exception, so a rollback there returns silently to the caller.
     * [transactionWithResult] lets it propagate, since there is no value to return on rollback.
     */
    fun rollback(): Nothing

    /**
     * Run [body] in a nested transaction. A nested rollback rolls back the enclosing transaction
     * too (no savepoint semantics).
     */
    fun transaction(body: SqkonTransactionScope.() -> Unit)
}

/**
 * Thrown out of [transactionWithResult] when [SqkonTransactionScope.rollback] is called: a
 * result-returning transaction has no value to hand back on rollback, so it aborts by throwing.
 */
class SqkonRollbackException internal constructor() :
    RuntimeException("Sqkon transaction rolled back")
