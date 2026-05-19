package com.mercury.sqkon.db.internal

import app.cash.sqldelight.TransacterImpl
import com.mercury.sqkon.db.driverFactory
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonTransaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SqkonTransactionForwardingTest {

    private val sqlDriver = driverFactory().createDriver()
    private val transacter = object : TransacterImpl(sqlDriver) {}

    @AfterTest
    fun tearDown() {
        sqlDriver.close()
    }

    @Test
    fun afterCommit_fires_when_transaction_commits() {
        var fired = false
        transacter.transaction {
            val sqlTrx = sqlDriver.currentTransaction()!!
            val wrapped: SqkonTransaction = SqlDelightSqkonTransaction(sqlTrx)
            wrapped.afterCommit { fired = true }
        }
        assertTrue(fired)
    }

    @Test
    fun afterRollback_fires_on_rollback() {
        var fired = false
        transacter.transaction {
            val sqlTrx = sqlDriver.currentTransaction()!!
            val wrapped: SqkonTransaction = SqlDelightSqkonTransaction(sqlTrx)
            wrapped.afterRollback { fired = true }
            rollback()
        }
        assertTrue(fired)
    }
}
