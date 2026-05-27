package com.mercury.sqkon.db.internal

import com.mercury.sqkon.db.driverFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqkonTransacterParentTest {

    private val driver = driverFactory().createDriver()
    private val transacter = SqkonTransacter(driver)

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun nested_transaction_parentHash_equals_outer_hash() {
        var outerHash = 0
        var innerParentHash = 0
        transacter.transaction {
            outerHash = transacter.currentOutermostTransactionHash()
            transacter.transaction {
                innerParentHash = transacter.currentOutermostTransactionHash()
            }
        }
        assertEquals(outerHash, innerParentHash)
    }

    @Test
    fun top_level_transaction_parentHash_equals_self() {
        var selfHash = 0
        var parentHash = 0
        transacter.transaction {
            selfHash = driver.currentTransaction()!!.hashCode()
            parentHash = transacter.currentOutermostTransactionHash()
        }
        assertEquals(selfHash, parentHash)
    }
}
