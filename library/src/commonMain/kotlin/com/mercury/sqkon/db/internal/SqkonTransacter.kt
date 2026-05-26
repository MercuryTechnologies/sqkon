package com.mercury.sqkon.db.internal

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.SqlDriver

/**
 * Sqkon-owned transacter. Rides `TransacterImpl(SqlDriver)` and tracks each transaction's
 * child -> parent link ([trxMap]) so [currentOutermostTransactionHash] can identify the outermost
 * enclosing transaction — used to dedup per-transaction side effects across nested writes.
 *
 * Still SQLDelight-backed; Phase 6 swaps the constructor to `SqkonDriver` and drops `TransacterImpl`.
 */
open class SqkonTransacter(driver: SqlDriver) : TransacterImpl(driver) {

    // Child -> Parent
    protected val trxMap = mutableMapOf<Transacter.Transaction, Transacter.Transaction?>()

    /**
     * Stable identity hash of the *outermost* transaction enclosing the one currently running on
     * [driver]. Used to dedup per-transaction side effects (e.g. the metadata write_at touch) so a
     * batch of nested writes only schedules one afterCommit. Must be called inside a transaction.
     */
    internal fun currentOutermostTransactionHash(): Int {
        val current = driver.currentTransaction()
            ?: error("currentOutermostTransactionHash() called outside a transaction")
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
