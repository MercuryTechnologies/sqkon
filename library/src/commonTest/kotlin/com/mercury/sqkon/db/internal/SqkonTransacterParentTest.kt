package com.mercury.sqkon.db.internal

import com.mercury.sqkon.db.driverFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqkonTransacterParentTest {

    private val sqlDriver = driverFactory().createDriver()
    private val transacter = SqkonTransacter(sqlDriver)

    @AfterTest
    fun tearDown() {
        sqlDriver.close()
    }

    @Test
    fun nested_transaction_parentHash_equals_outer_hash() {
        var outerHash = 0
        var innerParentHash = 0
        transacter.transaction {
            outerHash = sqlDriver.currentTransaction()!!.hashCode()
            transacter.transaction {
                with(transacter) {
                    innerParentHash = sqlDriver.currentTransaction()!!.parentTransactionHash()
                }
            }
        }
        assertEquals(outerHash, innerParentHash)
    }

    @Test
    fun top_level_transaction_parentHash_equals_self() {
        var selfHash = 0
        var parentHash = 0
        transacter.transaction {
            with(transacter) {
                val current = sqlDriver.currentTransaction()!!
                selfHash = current.hashCode()
                parentHash = current.parentTransactionHash()
            }
        }
        assertEquals(selfHash, parentHash)
    }
}
