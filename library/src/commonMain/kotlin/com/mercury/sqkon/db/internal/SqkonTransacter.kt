package com.mercury.sqkon.db.internal

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.SqlDriver

/**
 * Sqkon-owned transacter. Phase 2 still rides `TransacterImpl(SqlDriver)` so
 * `KeyValueStorage : Transacter by transacter` keeps working publicly.
 * Phase 5 flips the constructor to take `SqkonDriver` and drops the
 * `TransacterImpl` extension behind a `feat!:` 2.0 bump.
 */
open class SqkonTransacter(driver: SqlDriver) : TransacterImpl(driver) {

    // Child -> Parent
    protected val trxMap = mutableMapOf<Transacter.Transaction, Transacter.Transaction?>()

    /**
     * Stable identity hash of the *outermost* transaction enclosing the one currently running on
     * [driver]. Used to dedup per-transaction side effects (e.g. the metadata write_at touch) so a
     * batch of nested writes only schedules one afterCommit. Must be called inside a transaction.
     */
    internal fun currentParentTransactionHash(): Int {
        val current = driver.currentTransaction()
            ?: error("currentParentTransactionHash() called outside a transaction")
        return current.outermostHash()
    }

    private fun Transacter.Transaction.outermostHash(): Int =
        trxMap[this]?.outermostHash() ?: this.hashCode()

    override fun transaction(
        noEnclosing: Boolean,
        body: TransactionWithoutReturn.() -> Unit,
    ) {
        val parentTrx = driver.currentTransaction()
        return super.transaction(
            noEnclosing = noEnclosing,
            body = {
                val currentTrx = driver.currentTransaction()!!
                trxMap[currentTrx] = parentTrx
                try {
                    body()
                } finally {
                    trxMap.remove(currentTrx)
                }
            },
        )
    }

    override fun <R> transactionWithResult(
        noEnclosing: Boolean,
        bodyWithReturn: TransactionWithReturn<R>.() -> R,
    ): R {
        val parentTrx = driver.currentTransaction()
        return super.transactionWithResult(
            noEnclosing = noEnclosing,
            bodyWithReturn = {
                val currentTrx = driver.currentTransaction()!!
                trxMap[currentTrx] = parentTrx
                try {
                    bodyWithReturn()
                } finally {
                    trxMap.remove(currentTrx)
                }
            },
        )
    }
}
