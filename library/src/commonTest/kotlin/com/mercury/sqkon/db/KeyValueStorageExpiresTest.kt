package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class KeyValueStorageExpiresTest {

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
    fun insertAll() = runTest {
        val now = Clock.System.now()
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected, expiresAt = now.minus(1.milliseconds))
        val actual = testObjectStorage.selectAll(expiresAfter = now).first()
        assertEquals(0, actual.size)
    }

    @Test
    fun update() = runTest {
        val now = Clock.System.now()
        val inserted = TestObject()
        testObjectStorage.insert(inserted.id, inserted, expiresAt = now.minus(1.milliseconds))
        val actualInserted = testObjectStorage.selectAll(expiresAfter = now).first()
        assertEquals(0, actualInserted.size)
        // update with new expires
        testObjectStorage.update(inserted.id, inserted, expiresAt = now.plus(1.milliseconds))
        val actualUpdated = testObjectStorage.selectAll(expiresAfter = now).first()
        assertEquals(1, actualUpdated.size)
    }

    @Test
    fun upsert() = runTest {
        val now = Clock.System.now()
        val inserted = TestObject()
        testObjectStorage.upsert(inserted.id, inserted, expiresAt = now.minus(1.milliseconds))
        val actualInserted = testObjectStorage.selectAll(expiresAfter = now).first()
        assertEquals(0, actualInserted.size)
        testObjectStorage.upsert(inserted.id, inserted, expiresAt = now.plus(1.milliseconds))
        val actualUpdated = testObjectStorage.selectAll(expiresAfter = now).first()
        assertEquals(1, actualUpdated.size)
    }

    @Test
    fun deleteExpired() = runTest {
        val now = Clock.System.now()
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected, expiresAt = now.minus(1.milliseconds))
        val actual = testObjectStorage.selectAll().first() // all results
        assertEquals(expected.size, actual.size)

        testObjectStorage.deleteExpired(expiresAfter = now)
        // No expires to return everything
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(0, actualAfterDelete.size)
    }

    @Test
    fun delete_byEntityId() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)

        val key = expected.keys.toList()[5]
        testObjectStorage.delete(
            where = TestObject::id eq key
        )
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size - 1, actualAfterDelete.size)
    }

    @Test
    fun count() = runTest {
        val now = Clock.System.now()
        val empty = testObjectStorage.count().first()
        assertEquals(expected = 0, empty)

        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected, expiresAt = now.minus(1.milliseconds))
        val zero = testObjectStorage.count(expiresAfter = now).first()
        assertEquals(0, zero)
        val ten = testObjectStorage.count(expiresAfter = now.minus(1.milliseconds)).first()
        assertEquals(expected.size, ten)
    }

}
