package com.mercury.sqkon.db.utils

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.SqlDriver

open class SqkonTransacter(driver: SqlDriver) : TransacterImpl(driver) {

    // Child -> Parent
    protected val trxMap = mutableMapOf<Transacter.Transaction, Transacter.Transaction?>()

    fun Transacter.Transaction.parentTransactionHash(): Int {
        // Get enclosing transaction, otherwise we're probably top level, use that hashcode
        return trxMap[this]?.parentTransactionHash() ?: this.hashCode()
    }

    override fun transaction(
        noEnclosing: Boolean,
        body: TransactionWithoutReturn.() -> Unit
    ) {
        val parentTrx = driver.currentTransaction()
        return super.transaction(
            noEnclosing = noEnclosing,
            body = {
                // Inside transaction here
                val currentTrx = driver.currentTransaction()!!
                trxMap[currentTrx] = parentTrx
                try {
                    body()
                } finally {
                    // End of transaction
                    trxMap.remove(currentTrx)
                }
            }
        )
    }

    override fun <R> transactionWithResult(
        noEnclosing: Boolean,
        bodyWithReturn: TransactionWithReturn<R>.() -> R
    ): R {
        val parentTrx = driver.currentTransaction()
        return super.transactionWithResult(
            noEnclosing = noEnclosing,
            bodyWithReturn = {
                // Inside transaction here
                val currentTrx = driver.currentTransaction()!!
                trxMap[currentTrx] = parentTrx
                try {
                    bodyWithReturn()
                } finally {
                    // End of transaction
                    trxMap.remove(currentTrx)
                }
            }
        )
    }

}
