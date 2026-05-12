package com.mercury.sqkon.db

import app.cash.sqldelight.db.QueryResult
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
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
        entityQueries.sqlDriver.executeQuery(
            identifier = null,
            sql = "SELECT read_at, write_at FROM entity WHERE entity_key = ? AND entity_name = 'md'",
            mapper = { c ->
                c.next().value  // advance cursor — returns Boolean in SQLDelight 2.3.x
                readAt = c.getLong(0)
                writeAt = c.getLong(1)
                QueryResult.Unit
            },
            parameters = 1,
        ) { bindString(0, key) }
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
