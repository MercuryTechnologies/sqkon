package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import com.mercury.sqkon.until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
                OrderBy(TestObject::child.then(TestObjectChild::createdAt), OrderDirection.DESC)
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
            where = TestObject::id eq expect.id
        ).first()

        assertEquals(1, actualsById.size)
        assertEquals(expect, actualByKey)
        assertEquals(expect, actualsById.first())
    }


    @Test
    fun select_byEntityInlineValue() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[5]
        val actualByInlineValue = testObjectStorage.select(
            // This works, but need to work on an API which would ignore the value class if passed in
            where = TestObject::testValue eq expect.testValue.test
        ).first()

        assertEquals(1, actualByInlineValue.size)
        assertEquals(expect, actualByInlineValue.first())
    }

    @Test
    fun select_byEntityInlineValueInner() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[5]
        val actualByInlineValue = testObjectStorage.select(
            // This works, but need to work on an API which would ignore the value class if passed in
            where = TestObject::testValue eq expect.testValue.test
        ).first()

        assertEquals(1, actualByInlineValue.size)
        assertEquals(expect, actualByInlineValue.first())
    }

    @Test
    fun select_byEntityChildField() = runTest {
        val expected = (0..10).map {
            TestObject(
                child = TestObjectChild(
                    createdAt = Clock.System.now().plus(it.seconds)
                )
            )
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[5]
        val actualByInlineValue = testObjectStorage.select(
            where = TestObject::child.then(TestObjectChild::createdAt) lt expect.child.createdAt.toString(),
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).first()

        assertEquals(5, actualByInlineValue.size)
        assertEquals(expected.values.take(5), actualByInlineValue)
    }

    @Test
    fun select_byEntityAttributeList() = runTest {
        val expected = (1..10).map { num ->
            TestObject(attributes = listOf("${num}1", "${num}2"))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[1]
        val actualByAttributes = testObjectStorage.select(
            where = TestObject::attributes eq "22",
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).first()

        println(expect.attributes)
        assertEquals(1, actualByAttributes.size)
        assertEquals(expect, actualByAttributes.first())
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
            where = TestObject::id eq key
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

    @Test
    fun selectAllFlow_flowUpdatesOnInsert() = runTest {
        val results: MutableList<List<TestObject>> = mutableListOf()
        backgroundScope.launch {
            testObjectStorage.selectAll(
                orderBy = listOf(OrderBy(TestObject::id, direction = OrderDirection.ASC))
            ).collect { results.add(it) }
        }
        // Wait for first result
        until { results.isNotEmpty() }
        assertEquals(0, results.first().size)

        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)

        until { results.size == 2 }

        assertEquals(expected.size, results[1].size)
        assertEquals(expected.values.toList(), results[1])
    }

    @Test
    fun selectAllFlow_flowUpdatesOnUpdate() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        val results: MutableList<List<TestObject>> = mutableListOf()
        backgroundScope.launch {
            testObjectStorage.selectAll(
                orderBy = listOf(OrderBy(TestObject::id, direction = OrderDirection.ASC))
            ).collect { results.add(it) }
        }

        until { results.size == 1 }
        assertEquals(expected.size, results.first().size)

        val updated = expected.values.toList()[5].copy(
            name = "Updated Name",
            value = 12345,
            description = "Updated Description",
            child = expected.values.toList()[5].child.copy(updatedAt = Clock.System.now())
        )
        testObjectStorage.update(updated.id, updated)
        until { results.size == 2 }

        assertEquals(expected.size, results[1].size)
        assertEquals(updated, results[1][5])
    }

    @Test
    fun selectAllFlow_flowUpdatesOnDelete() = runTest {
        val expected = (0..10).map { TestObject() }
            .associateBy { it.id }
            .toSortedMap()
        testObjectStorage.insertAll(expected)
        val results: MutableList<List<TestObject>> = mutableListOf()
        backgroundScope.launch {
            testObjectStorage.selectAll(
                orderBy = listOf(OrderBy(TestObject::id, direction = OrderDirection.ASC))
            ).collect { results.add(it) }
        }

        until { results.size == 1 }
        assertEquals(expected.size, results.first().size)

        val key = expected.keys.toList()[5]
        testObjectStorage.deleteByKey(key)
        until { results.size == 2 }

        assertEquals(expected.size - 1, results[1].size)
    }

    @Test
    fun selectCount_flowUpdatesOnChange() = runTest {
        val results: MutableList<Long> = mutableListOf()
        backgroundScope.launch {
            testObjectStorage.count().collect { results.add(it) }
        }
        // Wait for first result
        until { results.isNotEmpty() }
        assertEquals(expected = 0, results.first())

        TestObject().also { testObjectStorage.insert(it.id, it) }
        until { results.size == 2 }
        assertEquals(expected = 1, results[1])

        testObjectStorage.deleteAll()
        until { results.size == 3 }
        assertEquals(expected = 0, results[2])
    }


}
