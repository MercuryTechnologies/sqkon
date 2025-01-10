package com.mercury.sqkon.db

import app.cash.turbine.test
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Test
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun updateWrite_onInsert() = runTest {
        val expected = (1..20).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        testObjectStorage.metadata().test {
            sleep(1)
            val now = Clock.System.now()
            awaitItem().also {
                assertEquals("test-object", it.entity_name)
                assertNotNull(it.lastWriteAt)
                assertNull(it.lastReadAt)
                assertTrue { it.lastWriteAt!! <= now }
            }
        }
    }

    @Test
    fun updateWrite_onUpdate() = runTest {
        val expected = (1..20).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        testObjectStorage.metadata().test {
            val now = Clock.System.now()
            sleep(2)
            awaitItem()
            testObjectStorage.updateAll(expected)
            awaitItem().also {
                assertEquals("test-object", it.entity_name)
                assertNotNull(it.lastWriteAt)
                assertNull(it.lastReadAt)
                assertTrue { it.lastWriteAt!! > now }
            }
        }
    }

    @Test
    fun updateRead_onSelect() = runTest {
        val expected = (1..20).map { _ -> TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        sleep(1)
        val now = Clock.System.now()
        sleep(2)
        testObjectStorage.metadata().test {
            awaitItem()
            testObjectStorage.selectAll().first()
            awaitItem().also {
                assertEquals("test-object", it.entity_name)
                assertNotNull(it.lastWriteAt)
                assertNotNull(it.lastReadAt)
                assertTrue { it.lastWriteAt!! <= now }
                assertTrue { it.lastReadAt!! > now }
            }
        }
    }

}
