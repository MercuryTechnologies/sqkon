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

    fun Transacter.Transaction.parentTransactionHash(): Int {
        // Walk up the chain. Top level: hash of self.
        return trxMap[this]?.parentTransactionHash() ?: this.hashCode()
    }

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
