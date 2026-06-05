package com.mercury.sqkon.db

import app.cash.turbine.test
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.until
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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

    @Suppress("DEPRECATION")
    @Test
    fun deleteState_delegatesToDeleteStale() = runTest {
        val expected = (0..4).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        // deleteState forwards to deleteStale(instant, instant); a far-future cutoff makes every
        // (write-stale) row qualify, so all rows are purged.
        testObjectStorage.deleteState(Clock.System.now().plus(1.days))
        assertEquals(0, testObjectStorage.selectAll().first().size)
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
        // Far-future cutoff: every row is write-stale (write_at < cutoff) and read-stale
        // (read_at IS NULL), so purgeStale removes them all — deterministic, no wall-clock gap.
        val cutoff = Clock.System.now().plus(1.days)
        testObjectStorage.deleteStale(writeInstant = cutoff, readInstant = cutoff)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(0, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleWrite_purgeReadNotStale() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first() // schedules the async read_at update
        // Wait until read_at is actually committed, so this validates read tracking (it would
        // otherwise pass vacuously while read_at is still NULL).
        until { testObjectStorage.selectResult().first().all { it.readAt != null } }
        // Far-past read cutoff: the freshly-committed read_at is not < cutoff, so purgeStaleRead
        // keeps every row — verifies a recently-read row is not read-stale.
        testObjectStorage.deleteStale(writeInstant = null, readInstant = Clock.System.now().minus(1.days))
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleWrite_purgeStaleWrite() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        // Far-future write cutoff: every row is write-stale, so purgeStaleWrite removes them all.
        testObjectStorage.deleteStale(writeInstant = Clock.System.now().plus(1.days), readInstant = null)
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
        // write again so the rows are NOT write-stale (write_at is fresh)
        testObjectStorage.updateAll(expected)
        // purgeStale requires write-stale AND read-stale. read cutoff far-future (read-stale),
        // write cutoff far-past (NOT write-stale) -> the AND fails -> rows are kept. Verifies the
        // AND semantics deterministically, without a wall-clock gap.
        testObjectStorage.deleteStale(
            writeInstant = Clock.System.now().minus(1.days),
            readInstant = Clock.System.now().plus(1.days),
        )
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_readInPast_purgeStaleRead() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first() // schedules the read_at update
        // read_at is updated asynchronously (fire-and-forget on the write dispatcher), so wait
        // until it has actually been committed before purging — otherwise read_at can still be
        // NULL when deleteStale runs and `read_at < cutoff` won't match (the rows wouldn't purge).
        until { testObjectStorage.selectResult().first().all { it.readAt != null } }
        // Bump write_at so the read is in the past relative to the latest write; read_at stays set.
        testObjectStorage.updateAll(expected)
        // Cutoff far in the future so every committed read_at deterministically qualifies as stale.
        // (The original cutoff — Clock.System.now() bracketed by sleep(1), one millisecond — also
        // raced clock granularity.)
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
        // write again so the rows are NOT write-stale (write_at is fresh)
        testObjectStorage.updateAll(expected)
        // Far-past write cutoff: write_at (fresh) is not < cutoff, so purgeStaleWrite keeps all.
        testObjectStorage.deleteStale(writeInstant = Clock.System.now().minus(1.days), readInstant = null)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actualAfterDelete.size)
    }

    @Test
    fun insertAll_staleRead() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        testObjectStorage.selectAll().first() // the rows have been read
        // Far-future cutoffs: rows are both write-stale and read-stale, so purgeStale removes all.
        val cutoff = Clock.System.now().plus(1.days)
        testObjectStorage.deleteStale(writeInstant = cutoff, readInstant = cutoff)
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
