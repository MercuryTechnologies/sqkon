package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.serializersModuleOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class KeyValueStorageTest {

    private val entityQueries = createEntityQueries()
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        serializersModule = serializersModuleOf(Uuid.serializer())
    }
    private val testObjectStorage = keyValueStorage<TestObject>("test-object", entityQueries, json)

    @Test
    fun insert() = runTest {
        val expected = TestObject()
        testObjectStorage.insert(expected.id.toString(), expected)
        val actual = testObjectStorage.selectAll().first().first()
        assertEquals(expected, actual)
    }

    @Test
    fun insertAll() = runTest {
        val expected = (0..10).map { TestObject() }
            .sortedBy { it.name }
            .associateBy { it.id.toString() }
        testObjectStorage.insertAll(expected)
        val actual = testObjectStorage.selectAll().first()
        assertEquals(actual.size, expected.size)
        assertEquals(expected.values.toList(), actual)
//        assertContains(expected.values.toList(), actual[0])
//        assertContains(expected.values.toList(), actual[1])
//        assertContains(expected.values.toList(), actual[2])
//        assertContains(expected.values.toList(), actual[3])
//        assertContains(expected.values.toList(), actual[4])
//        assertContains(expected.values.toList(), actual[5])
//        assertContains(expected.values.toList(), actual[6])
//        assertContains(expected.values.toList(), actual[7])
//        assertContains(expected.values.toList(), actual[8])
//        assertContains(expected.values.toList(), actual[9])
    }

}
