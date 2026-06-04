package com.mercury.sqkon.db

import app.cash.turbine.test
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import java.lang.Thread.sleep
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KeyValueStorageStaleTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @AfterTest
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun deleteStale_wakesActiveSelectAllSubscriber() = runTest {
        val expected = (0..4).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        testObjectStorage.selectAll().test {
            assertEquals(expected.size, awaitItem().size)
            // Pick a cutoff well in the future so every row's `write_at` and
            // `read_at` qualifies for purge. Regression guard for the contract
            // that MetadataQueries.purgeStale/purgeStaleWrite/purgeStaleRead fire
            // entityKey(name), not only the broad table key.
            val far = Clock.System.now().plus(1.days)
            testObjectStorage.deleteStale(writeInstant = far, readInstant = far)
            assertEquals(0, awaitItem().size)
        }
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
    fun insertAll_staleWrite_purgeReadNotStale() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        sleep(1)
        val now = Clock.System.now()
        sleep(1)
        testObjectStorage.selectAll().first()
        // Clean up older than now
        testObjectStorage.deleteStale(writeInstant = null, readInstant = now)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleWrite_purgeStaleWrite() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        sleep(1)
        val now = Clock.System.now()
        sleep(1)
        testObjectStorage.selectAll().first()
        // Clean up older than now
        testObjectStorage.deleteStale(writeInstant = now, readInstant = null)
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
    fun insertAll_readInPast_purgeStaleRead() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first() // sets read_at on every row
        // Bump write_at so the read is in the past relative to the latest write; read_at is left
        // as set above.
        testObjectStorage.updateAll(expected)
        // Cutoff far in the future so every row's read_at deterministically qualifies as stale.
        // The previous cutoff — Clock.System.now() bracketed by sleep(1) (one millisecond) — raced
        // clock granularity: when read_at and the cutoff landed in the same millisecond the rows
        // were not purged, so the selectAll subscriber stayed at 11 rows and the follow-up
        // awaitItem timed out (flaky on slower CI machines).
        val readCutoff = Clock.System.now().plus(1.days)
        testObjectStorage.deleteStale(writeInstant = null, readInstant = readCutoff)
        assertEquals(0, testObjectStorage.selectAll().first().size)
    }

    @Test
    fun insertAll_readInPast_purgeWriteNotStale() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first()
        val now = Clock.System.now()
        sleep(10)
        // write again so read is in the past
        testObjectStorage.updateAll(expected)
        // Read in the past write is after now
        testObjectStorage.deleteStale(writeInstant = now, readInstant = null)
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

    @Test
    fun selectResult_readWriteSet() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectResult().first()
        actual.forEach { result ->
            assertNotNull(result.readAt)
            assertNotNull(result.value)
        }
    }

}
