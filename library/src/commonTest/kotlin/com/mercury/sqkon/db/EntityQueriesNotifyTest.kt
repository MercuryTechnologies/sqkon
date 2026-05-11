package com.mercury.sqkon.db

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.expectNoEventsBriefly
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class EntityQueriesNotifyTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storeA = keyValueStorage<TestObject>("store-a", entityQueries, metadataQueries, mainScope)
    private val storeB = keyValueStorage<TestObject>("store-b", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    @Test
    fun writeToStoreA_doesNotEmitOnFlowOverStoreB() = runTest {
        turbineScope {
            val flowB = storeB.selectAll().testIn(backgroundScope)
            assertEquals(emptyList(), flowB.awaitItem()) // initial

            storeA.insert("k1", TestObject())

            flowB.expectNoEventsBriefly()
            flowB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sameNameCrossInstanceWrite_doesEmit() = runTest {
        // Two KeyValueStorage instances over the SAME driver and SAME entityName must
        // share the driver-level "entity_<name>" listener.
        val storeA2 = keyValueStorage<TestObject>("store-a", entityQueries, metadataQueries, mainScope)
        storeA.selectAll().test {
            assertEquals(emptyList(), awaitItem())
            val expected = TestObject()
            storeA2.insert(expected.id, expected)
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(expected, emitted.first())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
