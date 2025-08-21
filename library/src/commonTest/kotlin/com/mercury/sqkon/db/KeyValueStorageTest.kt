package com.mercury.sqkon.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.logs.LogSqliteDriver
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.mercury.sqkon.TestEnum
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import com.mercury.sqkon.TestValue
import com.mercury.sqkon.until
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class KeyValueStorageTest {

    private val mainScope = MainScope()
    private val driver = LogSqliteDriver(driverFactory().createDriver()) {
        println(it)
    }
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
    fun upsert() = runTest {
        val inserted = TestObject()
        testObjectStorage.upsert(inserted.id, inserted)
        val actualInserted = testObjectStorage.selectAll().first().first()
        assertEquals(inserted, actualInserted)
        val updated = inserted.copy(
            name = "Updated Name",
            value = 12345,
            description = "Updated Description",
            child = inserted.child.copy(updatedAt = Clock.System.now())
        )
        testObjectStorage.upsert(updated.id, updated)
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
    fun select_byEnumValue() = runTest {
        val expected = (0..2).map {
            TestObject(testEnum = TestEnum.entries[it])
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val expect = expected.values.toList()[0]
        val actualByEnumValue = testObjectStorage.select(
            where = TestObject::testEnum eq TestEnum.FIRST
        ).first()

        assertEquals(1, actualByEnumValue.size)
        assertEquals(expect, actualByEnumValue.first())
    }

    @Test
    fun select_withLimit1() = runTest {
        val expected = (0..10).map {
            TestObject(child = TestObjectChild(createdAt = Clock.System.now().plus(it.seconds)))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val expect = expected.values.toList()[0]
        val actualsByLimit = testObjectStorage.select(
            limit = 1,
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).first()

        assertEquals(1, actualsByLimit.size)
        assertEquals(expect, actualsByLimit.first())
    }

    @Test
    fun select_withLimitAndOffset1() = runTest {
        val expected = (0..10).map {
            TestObject(child = TestObjectChild(createdAt = Clock.System.now().plus(it.seconds)))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

        val expect = expected.values.toList()[1]
        val actualsByLimit = testObjectStorage.select(
            limit = 1, offset = 1,
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt))),
        ).first()

        assertEquals(1, actualsByLimit.size)
        assertEquals(expect, actualsByLimit.first())
    }


    @Test
    fun select_byEntityInlineValue() = runTest {
        val expected = (0..10).map { TestObject(testValue = TestValue(it.toString())) }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[5]
        val actualByInlineValue = testObjectStorage.select(
            where = TestObject::testValue eq TestValue("5")
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
            where = TestObject::child.then(TestObjectChild::createdAt) lt expect.child.createdAt,
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).first()

        assertEquals(5, actualByInlineValue.size)
        assertEquals(expected.values.take(5), actualByInlineValue)
    }

    @Test
    fun select_byAndEntityChildField() = runTest {
        turbineScope {
            val insert = (1..2).map {
                TestObject(child = TestObjectChild(createdAt = Clock.System.now().plus(it.seconds)))
            }.associateBy { it.id }
            testObjectStorage.insertAll(insert)
            val expect1 = TestObject(name = "ThisName", description = "ThatDescription")
            val expect2 = TestObject(name = "ThatName", description = "ThisDescription")
            testObjectStorage.insert(expect1.id, expect1)
            testObjectStorage.insert(expect2.id, expect2)

            val actual1 = testObjectStorage.select(
                where = (TestObject::name eq "ThisName")
                    .and(TestObject::description eq "ThatDescription")
            ).testIn(backgroundScope)
            val a1Item = actual1.awaitItem()

            val actual2 = testObjectStorage.select(
                where = (TestObject::name eq "ThisName").or(TestObject::description eq "ThatName")
            ).testIn(backgroundScope)
            val a2Item = actual2.awaitItem()

            assertEquals(expect1, a1Item.first())
            assertEquals(expect1, a2Item.first())
        }
    }

    @Test
    fun select_byEntityAttributeList() = runTest {
        val expected = (1..10).map { num ->
            TestObject(attributes = listOf("${num}1", "${num}2"))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)

//        val result = driver.executeQuery(
//            null,
//            """
//                SELECT entity.entity_name, list_1.value
//                FROM entity, json_tree(entity.value, '$') as contains_1, json_each(contains_1.value) as list_1
//                WHERE entity_name = ? AND (contains_1.fullkey LIKE ? AND list_1.value = ?)
//            """.trimIndent(),
//            { cursor ->
//                QueryResult.Value(
//                    buildList {
//                        while (cursor.next().value) {
//                            add(cursor.getString(0) + "[" + cursor.getString(1) + "]")
//                        }
//                    }
//                )
//            },
//            3,
//            {
//                bindString(0, "test-object")
//                bindString(1, "${'$'}.attributes")
//                bindString(2, "22")
//            }
//        )
//        println(result.value)

        val expect = expected.values.toList()[1]
        val actualByAttributes = testObjectStorage.select(
            where = TestObject::attributes eq listOf("21", "22"),
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).first()

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
    fun delete_byKeys() = runTest {
        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(expected.size, actual.size)

        val key1 = expected.keys.toList()[5]
        val key2 = expected.keys.toList()[6]
        testObjectStorage.deleteByKeys(key1, key2)
        val actualAfterDelete = testObjectStorage.selectAll().first()
        assertEquals(expected.size - 2, actualAfterDelete.size)
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
    fun delete_byEntityAttributeList() = runTest {
        val expected = (1..10).map { num ->
            TestObject(attributes = listOf("${num}1", "${num}2"))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val expect = expected.values.toList()[1]
        testObjectStorage.delete(where = TestObject::attributes eq expect.attributes)
        val result = testObjectStorage.selectByKey(expect.id).first()
        assertNull(result)
    }

    @Test
    fun count() = runTest {
        val empty = testObjectStorage.count().first()
        assertEquals(expected = 0, empty)

        val expected = (0..10).map { TestObject() }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val ten = testObjectStorage.count().first()
        assertEquals(expected.size, ten)
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
        turbineScope {
            val expected = (0..10).map { TestObject() }
                .associateBy { it.id }
                .toSortedMap()
            testObjectStorage.insertAll(expected)
            val results = testObjectStorage.selectAll(
                orderBy = listOf(OrderBy(TestObject::id, direction = OrderDirection.ASC))
            ).testIn(backgroundScope)

            val first = results.awaitItem()
            assertEquals(expected.size, first.size)

            val updated = expected.values.toList()[5].copy(
                name = "Updated Name",
                value = 12345,
                description = "Updated Description",
                child = expected.values.toList()[5].child.copy(updatedAt = Clock.System.now())
            )
            testObjectStorage.update(updated.id, updated)
            val second = results.awaitItem()
            assertEquals(expected.size, second.size)
            assertEquals(updated, second[5])
        }
    }

    @Test
    fun selectAllFlow_flowUpdatesOnDelete() = runTest {
        turbineScope {
            val expected = (0..10).map { TestObject() }
                .associateBy { it.id }
                .toSortedMap()
            testObjectStorage.insertAll(expected)
            val results = testObjectStorage.selectAll(
                orderBy = listOf(OrderBy(TestObject::id, direction = OrderDirection.ASC))
            ).testIn(backgroundScope)

            val first = results.awaitItem()
            assertEquals(expected.size, first.size)

            val key = expected.keys.toList()[5]
            testObjectStorage.deleteByKey(key)
            val second = results.awaitItem()
            assertEquals(expected.size - 1, second.size)
        }
    }


    @Test
    fun selectCount_flowUpdatesOnChange() = runTest {
        testObjectStorage.count().test {
            // Wait for first result
            val first = awaitItem()
            assertEquals(expected = 0, first)

            TestObject().also { testObjectStorage.insert(it.id, it) }
            val second = awaitItem()
            assertEquals(expected = 1, second)

            testObjectStorage.deleteAll()
            val third = awaitItem()
            assertEquals(expected = 0, third)

            expectNoEvents()
        }
    }

    @Test
    fun selectCount_flowUpdatesOnUpsertOnce() = runTest {
        entityQueries.slowWrite = true
        val to1 = TestObject().also { testObjectStorage.upsert(it.id, it) }
        testObjectStorage.selectAll(
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).test {
            assertEquals(listOf(to1), awaitItem())
            // Insert new item
            val to = TestObject()
            testObjectStorage.upsert(to.id, to)
            awaitItem().also {
                assertEquals(2, it.size)
                assertEquals(listOf(to1, to), it)
            }
            // Insert new item
            TestObject().also { testObjectStorage.upsert(it.id, it) }
            awaitItem()
            // Update existing item
            val new = to.copy(
                name = "Updated Name",
                value = 12345,
                description = "Updated Description",
                child = to.child.copy(updatedAt = Clock.System.now())
            )
            testObjectStorage.upsert(new.id, new)
            awaitItem()
            // Check only one update (not two) for the upsert
        }
    }

    @Test
    fun selectCount_flowUpdatesOnUpsertAllOnce() = runTest {
        entityQueries.slowWrite = true
        val to1 = TestObject().also { testObjectStorage.upsert(it.id, it) }
        val to2 = TestObject().also { testObjectStorage.upsert(it.id, it) }
        testObjectStorage.selectAll(
            orderBy = listOf(OrderBy(TestObject::child.then(TestObjectChild::createdAt)))
        ).test {
            assertEquals(listOf(to1, to2), awaitItem())
            // Insert new item
            val to3 = TestObject()
            testObjectStorage.upsertAll(listOf(to3).associateBy { it.id })
            awaitItem().also {
                assertEquals(3, it.size)
                assertEquals(listOf(to1, to2, to3), it)
            }
            expectNoEvents()
            testObjectStorage.upsertAll(listOf(to1, to2, to3).associateBy { it.id })
            awaitItem() // should only emit once for all the upserts
            expectNoEvents()
        }
    }

    @Test
    fun select_inList() = runTest {
        val expectedO = TestObject()
        testObjectStorage.insert(expectedO.id, expectedO)

        val expectedList = expectedO.list.map { it.createdAt.toString() }

        val actual = testObjectStorage.select(
            where = TestObject::list.then(TestObjectChild::createdAt) inList expectedList
        ).first()

        assertEquals(expectedO, actual.first())
        assertEquals(expectedO.list, actual.first().list)
    }

    @Test
    fun select_inList_valueClass() = runTest {
        val expectedO = TestObject().copy(
            testValue = TestValue("status")
        )
        testObjectStorage.insert(expectedO.id, expectedO)

        val actual = testObjectStorage.select(
            where = TestObject::testValue inList listOf(TestValue("status"))
        ).first()

        assertEquals(expectedO, actual.first())
        assertEquals(expectedO.list, actual.first().list)
    }

    @Test
    fun externalTransaction() = runTest {
        testObjectStorage.selectAll().test {
            testObjectStorage.transaction {
                testObjectStorage.deleteAll()
                testObjectStorage.insertAll(
                    (0..100).map { TestObject() }
                        .associateBy { it.id }
                        .toSortedMap()
                )
            }
            awaitItem()
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun updateWriteAtOnlyRunsOncePerTransaction() = runTest {
        turbineScope {
            val collect = testObjectStorage.metadata().testIn(backgroundScope)
            testObjectStorage.transaction {
                with(testObjectStorage) {
                    updateWriteAt()
                    updateWriteAt()
                }
            }
            collect.awaitItem()
            collect.expectNoEvents()
        }
    }


}
