package com.mercury.sqkon.db

import app.cash.turbine.test
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyValueStorageMetadataPerRowTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val store = keyValueStorage<TestObject>("md", entityQueries, metadataQueries, mainScope)

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    private fun rowMeta(key: String): Pair<Long?, Long?> {
        var readAt: Long? = null
        var writeAt: Long? = null
        entityQueries.driver.executeQuery(
            identifier = null,
            sql = "SELECT read_at, write_at FROM entity WHERE entity_key = ? AND entity_name = 'md'",
            parameters = 1,
            binders = { bindString(0, key) },
            mapper = { c ->
                c.next()
                readAt = c.getLong(0)
                writeAt = c.getLong(1)
            },
        )
        return readAt to writeAt
    }

    @Test
    fun insert_setsWriteAt_leavesReadAtNull() = runTest {
        val obj = TestObject()
        store.insert(obj.id, obj)
        val (readAt, writeAt) = rowMeta(obj.id)
        assertNotNull(writeAt)
        assertNull(readAt)
    }

    @Test
    fun select_updatesReadAt() = runTest {
        val obj = TestObject()
        store.insert(obj.id, obj)
        store.selectByKey(obj.id).first()
        until { rowMeta(obj.id).first != null }
        // updateReadAt is async; once until {} sees non-null read_at, the meta-write has landed.
        assertNotNull(rowMeta(obj.id).first)
    }

    @Test
    fun selectAllFlow_updateReadAtSideEffectDoesNotReEmit() = runTest {
        val expected = (0..2).map { TestObject() }.associateBy { it.id }
        store.insertAll(expected)

        store.selectAll().test {
            // Initial emission carries all rows. Right after this, the Flow chain's
            // .onEach { updateReadAt(...) } fires asynchronously and writes
            // entity.read_at via MetadataQueries.updateReadForEntities. That write
            // must NOT wake this same Flow — otherwise onEach re-fires, writes
            // again, and the subscription loops forever. Regression guard for
            // MetadataQueries.notifyEntityReadAtChanged staying single-key.
            assertEquals(expected.size, awaitItem().size)
            // Wait for the async updateReadAt to commit (read_at becomes non-null).
            until { rowMeta(expected.keys.first()).first != null }
            // updateReadAt has landed — no spurious emission must follow.
            cancelAndConsumeRemainingEvents().also { remaining ->
                assertTrue("Flow looped on updateReadAt; got extra events: $remaining") {
                    remaining.isEmpty()
                }
            }
        }
    }

    @Test
    fun count_doesNotUpdateReadAt() = runTest {
        val obj = TestObject()
        store.insert(obj.id, obj)
        store.count().first()
        delay(100)
        val (readAt, _) = rowMeta(obj.id)
        assertNull(readAt)
    }

    @Test
    fun update_advancesWriteAt() = runTest {
        val obj = TestObject()
        store.insert(obj.id, obj)
        val firstWrite = rowMeta(obj.id).second!!
        delay(5)
        store.update(obj.id, obj.copy(name = "new"))
        val secondWrite = rowMeta(obj.id).second!!
        assertTrue(secondWrite >= firstWrite)
    }

    @Test
    fun deleteExpired_fires_metadataWrite() = runTest {
        val past = Clock.System.now().minus(1.seconds)
        val obj = TestObject()
        store.insert(obj.id, obj, expiresAt = past)
        // Wait for the insert's upsertWrite (afterCommit) to land before snapshotting `before`,
        // then advance wall time so deleteExpired's upsertWrite produces a strictly-later
        // timestamp (Clock.System.now() has millisecond resolution on JVM).
        until { store.metadata().first().lastWriteAt != null }
        withContext(Dispatchers.Default) { delay(METADATA_TIMESTAMP_SPACER) }
        val before = store.metadata().first().lastWriteAt
        store.deleteExpired()
        until { (store.metadata().first().lastWriteAt ?: EPOCH) > (before ?: EPOCH) }
        assertEquals(0, store.count().first())
    }

    @Test
    fun deleteStale_fires_metadataWrite() = runTest {
        val obj = TestObject()
        store.insert(obj.id, obj)
        until { store.metadata().first().lastWriteAt != null }
        withContext(Dispatchers.Default) { delay(METADATA_TIMESTAMP_SPACER) }
        val before = store.metadata().first().lastWriteAt
        store.deleteStale(
            writeInstant = Clock.System.now(),
            readInstant = Clock.System.now(),
        )
        until { (store.metadata().first().lastWriteAt ?: EPOCH) > (before ?: EPOCH) }
    }

    private companion object {
        /** Wall-clock spacer ensuring two consecutive upsertWrite calls land at distinct ms. */
        val METADATA_TIMESTAMP_SPACER = 5.milliseconds
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
