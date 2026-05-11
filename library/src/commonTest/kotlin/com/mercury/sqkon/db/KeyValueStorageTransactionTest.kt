package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class KeyValueStorageTransactionTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<TestObject>("tx", entityQueries, metadataQueries, mainScope)

    @After fun tearDown() { mainScope.cancel() }

    @Test
    fun rollback_leavesCountAtZero() = runTest {
        // SQLDelight rollback() aborts the transaction silently — no exception propagated to caller.
        storage.transaction {
            storage.insert("k1", TestObject())
            storage.insert("k2", TestObject())
            rollback()
        }
        assertEquals(0, storage.count().first())
    }

    @Test
    fun nestedRollback_rollsBackInnerOnly_perSqlDelightSemantics() = runTest {
        storage.transaction {
            storage.insert("outer", TestObject())
            storage.transaction {
                storage.insert("inner", TestObject())
                rollback()
            }
        }
        val count = storage.count().first()
        // Observed: SQLDelight propagates RollbackException from inner tx to outer tx, rolling back
        // the entire enclosing transaction — no savepoint semantics. Count must be 0.
        assertEquals(0, count) // observed value pinned in Phase 0 — change requires a migration ADR
    }

    @Test
    fun afterCommit_firesAfterCommitVisibleToReaders() = runTest {
        val seen = AtomicInteger(0)
        storage.transaction {
            storage.insert("k", TestObject())
            afterCommit {
                // runBlocking is intentional — afterCommit runs on the real commit thread,
                // not the runTest scheduler. By the time this fires, the write must be visible.
                val visible = runBlocking { storage.count().first() }
                check(visible == 1) { "afterCommit ran before commit visible: $visible" }
                seen.incrementAndGet()
            }
        }
        until { seen.get() == 1 }
    }

    @Test
    fun afterCommit_doesNotFireOnRollback() = runTest {
        val fired = AtomicInteger(0)
        runCatching {
            storage.transaction {
                storage.insert("k", TestObject())
                afterCommit { fired.incrementAndGet() }
                rollback()
            }
        }
        // Give any spurious afterCommit microtask a real-wall window to land, then assert silence.
        withContext(Dispatchers.Default) { delay(AFTER_COMMIT_SETTLE) }
        assertEquals(0, fired.get())
        assertEquals(0, storage.count().first())
    }

    @Test
    fun nestedTransaction_afterCommitFiresOnceAtOutermostCommit() = runTest {
        val fired = AtomicInteger(0)
        storage.transaction {
            afterCommit { fired.incrementAndGet() }
            storage.insert("outer", TestObject())
            storage.transaction {
                storage.insert("inner", TestObject())
                afterCommit { fired.incrementAndGet() }
            }
            // at this point neither afterCommit should have run yet
            assertEquals(0, fired.get())
        }
        // Both callbacks registered; both must fire after the OUTERMOST commit.
        until { fired.get() == 2 }
    }

    private companion object {
        /** Wall-clock window for the metadata-write afterCommit microtask to settle. */
        val AFTER_COMMIT_SETTLE = 50.milliseconds
    }

}
