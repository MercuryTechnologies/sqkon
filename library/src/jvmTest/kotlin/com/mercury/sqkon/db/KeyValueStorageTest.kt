package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class KeyValueStorageTest {

    private val entityQueries = createEntityQueries()
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries
    )

    @Test
    fun insert() = runTest {
        val expected = TestObject()
        testObjectStorage.insert(expected.id, expected)
        val actual = testObjectStorage.selectAll().first().first()
        assertEquals(expected, actual)
    }

    @Test
    fun insertAll() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)
        assertEquals(expected.values.toList(), actual)
    }

    @Test
    fun update() = runTest {
        val inserted = TestObject()
        testObjectStorage.insert(inserted.id, inserted)
        val actualInserted = testObjectStorage.selectAll().first().first()
        assertEquals(inserted, actualInserted)
        val updated = inserted.copy(
            name = "Updated Name",
            value = 12345,
            description = "Updated Description",
            child = inserted.child.copy(updatedAt = Clock.System.now())
        )
        testObjectStorage.update(updated.id, updated)
        val actualUpdated = testObjectStorage.selectAll().first().first()
        assertEquals(updated, actualUpdated)
    }

    @Test
    fun selectAll_orderBy_EntityName() = runTest {
        val expected = (0..10).map { TestObject() }
            .sortedBy { it.name }
            .associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val actual = testObjectStorage.selectAll(
            orderBy = listOf(OrderBy(TestObject::name, direction = OrderDirection.ASC))
        ).first()

        assertEquals(expected.size, actual.size)
        assertEquals(expected.values.toList(), actual)
    }

    @Test
    fun selectAll_orderBy_EntityValueThenName() = runTest {
        val expected = (0..10).map { TestObject() }
            .sortedWith(compareBy({ it.value }, { it.name }))
            .associateBy { it.id }
        testObjectStorage.insertAll(expected)


        val actual = testObjectStorage.selectAll(
            orderBy = listOf(
                OrderBy(TestObject::value, direction = OrderDirection.ASC),
                OrderBy(TestObject::name, direction = OrderDirection.ASC),
            )
        ).first()

        assertEquals(expected.size, actual.size)
        assertEquals(expected.values.toList(), actual)
    }


    @Test
    fun selectAll_orderBy_EntityChildAddedBy() = runTest {
        val expected = (0..10)
            .map {
                TestObject(
                    child = TestObjectChild(createdAt = Clock.System.now().plus(it.seconds))
                )
            }
            .sortedByDescending { it.child.createdAt }
            .associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val actual = testObjectStorage.selectAll(
            orderBy = listOf(
                OrderBy(
                    TestObject::child, TestObjectChild::createdAt,
                    direction = OrderDirection.DESC
                ),
            )
        ).first()

        assertEquals(expected.size, actual.size)
        assertEquals(expected.values.toList(), actual)
    }


    @Test
    fun selectByKey() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val expect = expected.values.toList()[5]
        val actual = testObjectStorage.selectByKey(expect.id).first()

        assertEquals(actual, expect)
    }


    @Test
    fun select_byEntityId() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val expect = expected.values.toList()[5]
        val actualByKey = testObjectStorage.selectByKey(expect.id).first()

        val actualsById = testObjectStorage.select(
            where = Eq(TestObject::name, value = expect.id)
        ).first()

        assertEquals(1, actualsById.size)
        assertEquals(expect, actualByKey)
        assertEquals(expect, actualsById.first())
    }

    @Test
    fun deleteAll() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)

        testObjectStorage.deleteAll()
        val empty = testObjectStorage.selectAll().first()
        assertEquals(expected = 0, empty.size)
    }

    @Test
    fun delete_byKey() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)

        val key = expected.keys.toList()[5]
        testObjectStorage.deleteByKey(key)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size - 1, actualAfterDelete.size)
    }

    @Test
    fun delete_byEntityId() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)

        val key = expected.keys.toList()[5]
        testObjectStorage.delete(
            where = Eq(TestObject::id, value = key)
        )
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size - 1, actualAfterDelete.size)
    }

    @Test
    fun count() = runTest {
        val empty = testObjectStorage.count().first()
        assertEquals(expected = 0, empty)

        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val ten = testObjectStorage.count().first()
        assertEquals(expected.size.toLong(), ten)
    }

}
