// Adapted from app.cash.sqldelight:TransacterImpl 2.3.2 (Apache-2.0) for the transactionWithWrapper
// pattern; backed by Sqkon's own SqkonDriver/SqkonTransaction.
package com.mercury.sqkon.db.internal

import com.mercury.sqkon.db.SqkonTransactionScope
import com.mercury.sqkon.db.newScopeReceiver

/**
 * Drives transactions on a [SqkonDriver]. Provides:
 *   - `transaction { }` / `transactionWithResult { }` over [SqkonTransactionScope].
 *   - [currentOutermostTransactionHash] for dedup'ing per-transaction side effects across nesting.
 *   - [notifyQueries] helper for query subclasses to fire listeners.
 *
 * Subclassed by [com.mercury.sqkon.db.EntityQueries] and [com.mercury.sqkon.db.MetadataQueries]
 * so callers can do `entityQueries.transaction { … }` directly, matching the previous SQLDelight
 * `TransacterImpl` ergonomics.
 */
open class SqkonTransacter internal constructor(internal val driver: SqkonDriver) {

    fun transaction(noEnclosing: Boolean = false, body: SqkonTransactionScope.() -> Unit) {
        transactionWithWrapper<Unit>(noEnclosing) { body() }
    }

    fun <R> transactionWithResult(noEnclosing: Boolean = false, body: SqkonTransactionScope.() -> R): R =
        transactionWithWrapper(noEnclosing, body)

    private fun <R> transactionWithWrapper(
        noEnclosing: Boolean,
        body: SqkonTransactionScope.() -> R,
    ): R {
        val enclosing = driver.currentTransaction()
        check(enclosing == null || !noEnclosing) { "Already in a transaction" }
        val transaction = driver.newTransaction()
        val scope = newScopeReceiver(transaction, this)
        var returnValue: R? = null
        var thrown: Throwable? = null
        try {
            returnValue = scope.body()
            transaction.successful = true
        } catch (t: Throwable) {
            // Catches SqkonRollbackException AND any other failure. Successful stays false so the
            // top-level transaction issues ROLLBACK (and nested transactions mark the enclosing
            // childrenSuccessful=false). Then we re-throw — KeyValueStorage.runTransaction swallows
            // SqkonRollbackException at the outermost Unit-transaction boundary;
            // KeyValueStorage.runTransactionWithResult lets it propagate to the caller.
            thrown = t
        } finally {
            transaction.endTransaction()
        }
        if (thrown != null) throw thrown
        @Suppress("UNCHECKED_CAST")
        return returnValue as R
    }

    /**
     * Stable identity hash of the *outermost* transaction enclosing the one currently running on
     * [driver]. Used to dedup per-transaction side effects (e.g. metadata write_at) across nested
     * writes. Must be called inside a transaction.
     */
    internal fun currentOutermostTransactionHash(): Int {
        var t: SqkonTransaction = driver.currentTransaction()
            ?: error("currentOutermostTransactionHash() called outside a transaction")
        while (true) {
            val enclosing = t.enclosingTransaction ?: return t.hashCode()
            t = enclosing
        }
    }

    /**
     * Fire all listeners registered on the keys emitted by [queryList]. Replaces SQLDelight's
     * `TransacterImpl.notifyQueries(identifier, ...)`; [identifier] is unused here but kept on the
     * signature for source compatibility with the existing EntityQueries/MetadataQueries callers.
     */
    @Suppress("UNUSED_PARAMETER")
    protected fun notifyQueries(identifier: Int, queryList: (emit: (String) -> Unit) -> Unit) {
        val keys = mutableListOf<String>()
        queryList { keys.add(it) }
        if (keys.isNotEmpty()) driver.notifyListeners(queryKeys = keys.toTypedArray())
    }
}

