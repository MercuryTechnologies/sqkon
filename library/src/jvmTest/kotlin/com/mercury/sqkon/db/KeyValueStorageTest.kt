package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
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
        assertEquals(actual.size, expected.size)
        assertEquals(expected.values.toList(), actual)
    }

    @Test
    fun selectAll_orderBy_EntityName() = runTest {
        val expected = (0..10).map { TestObject() }
            .sortedBy { it.name }
            .associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val actual = testObjectStorage.selectAll(
            orderBy = listOf(OrderBy(TestObject::name.name, OrderDirection.ASC))
        ).first()

        assertEquals(actual.size, expected.size)
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
                OrderBy(TestObject::value.name, OrderDirection.ASC),
                OrderBy(TestObject::name.name, OrderDirection.ASC),
            )
        ).first()

        assertEquals(actual.size, expected.size)
        assertEquals(expected.values.toList(), actual)
    }


    @Test
    fun selectAll_orderBy_EntityChildAddedBy() = runTest {
        val expected = (0..10).map { TestObject() }
            .sortedBy { it.child.createdAt }
            .associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val actual = testObjectStorage.selectAll(
            orderBy = listOf(
                OrderBy(TestObject::child, TestObjectChild::createdAt),
            )
        ).first()

        assertEquals(actual.size, expected.size)
        assertEquals(expected.values.toList(), actual)
    }

}
