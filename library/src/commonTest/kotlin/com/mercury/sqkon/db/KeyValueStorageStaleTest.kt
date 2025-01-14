package com.mercury.sqkon.db

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

class KeyValueStorageStaleTest {

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
    fun insertAll_staleInPast() = runTest {
        val now = Clock.System.now()
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        // Clean up older than now
        testObjectStorage.deleteStale(writeInstant = now, readInstant = now)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleWrite() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        sleep(1)
        val now = Clock.System.now()
        // Clean up older than now
        testObjectStorage.deleteStale(writeInstant = now, readInstant = now)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(0, actualAfterDelete.size)
    }

    @Test
    fun insertAll_readInPast() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first()
        sleep(10)
        val now = Clock.System.now()
        // write again so read is in the past
        testObjectStorage.updateAll(expected)
        // Read in the past write is after now
        testObjectStorage.deleteStale(writeInstant = now, readInstant = now)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleRead() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        sleep(10)
        testObjectStorage.selectAll().first()
        sleep(10)
        val now = Clock.System.now()
        // Clean write and read are in the past
        testObjectStorage.deleteStale(writeInstant = now, readInstant = now)
        val actualAfterDelete = testObjectStorage.selectResult().first()
        assertEquals(0, actualAfterDelete.size)
    }

}
