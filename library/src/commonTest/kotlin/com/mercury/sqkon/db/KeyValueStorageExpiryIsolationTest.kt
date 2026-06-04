package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Regression test for the cross-store expiry leak (#67).
 *
 * The expiry predicate `expires_at IS NULL OR expires_at >= ?` was appended to the WHERE
 * fragment list un-parenthesized. `buildWhere` joins fragments with bare " AND ", and SQL
 * `AND` binds tighter than `OR`, so the assembled clause parsed as
 * `(entity_name = ? AND expires_at IS NULL) OR (expires_at >= ?)`. The `expires_at >= ?`
 * branch was therefore no longer scoped to `entity_name`, and non-expired rows from *other*
 * stores (which share the same driver/database) leaked into the result.
 */
class KeyValueStorageExpiryIsolationTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)

    // Two stores backed by the same driver/database — the precondition for the leak.
    private val storeA = keyValueStorage<TestObject>("store-a", entityQueries, metadataQueries, mainScope)
    private val storeB = keyValueStorage<TestObject>("store-b", entityQueries, metadataQueries, mainScope)

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun selectWithExpiresAfter_doesNotLeakRowsFromOtherStore() = runTest {
        val now = Clock.System.now()
        // store B holds non-expired rows with a *non-null* future expiry — exactly the rows
        // the un-parenthesized `OR expires_at >= ?` branch would leak into store A's query.
        val bObjects = (0..4).map { TestObject() }.associateBy { it.id }
        storeB.insertAll(bObjects, expiresAt = now.plus(1.hours))

        val aObjects = (0..2).map { TestObject() }.associateBy { it.id }
        storeA.insertAll(aObjects, expiresAt = now.plus(1.hours))

        val result = storeA.selectAll(expiresAfter = now).first()

        assertEquals(aObjects.keys, result.map { it.id }.toSet())
    }

    @Test
    fun selectWithExpiresAfterAndWhere_doesNotLeakRowsFromOtherStore() = runTest {
        val now = Clock.System.now()
        val bObjects = (0..4).map { TestObject() }.associateBy { it.id }
        storeB.insertAll(bObjects, expiresAt = now.plus(1.hours))

        val aObjects = (0..2).map { TestObject() }.associateBy { it.id }
        storeA.insertAll(aObjects, expiresAt = now.plus(1.hours))

        // An additional user predicate that matches every row (names are random, so this
        // constant never collides). With the broken precedence the leaking branch becomes
        // `(expires_at >= ? AND <userWhere>)`, which B's rows also satisfy.
        val result = storeA.select(
            where = TestObject::name neq "__no_such_name__",
            expiresAfter = now,
        ).first()

        assertEquals(aObjects.keys, result.map { it.id }.toSet())
    }
}
