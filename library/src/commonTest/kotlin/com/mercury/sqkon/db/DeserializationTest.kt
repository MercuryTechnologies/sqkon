package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.UnSerializable
import com.mercury.sqkon.until
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

class DeserializationTest {

    private val mainScope = MainScope()
    private val entityQueries = createEntityQueries()
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, mainScope
    )
    private val testObjectStorageError = keyValueStorage<UnSerializable>(
        "test-object", entityQueries, mainScope, config = KeyValueStorage.Config(
            deserializePolicy = KeyValueStorage.Config.DeserializePolicy.ERROR // default
        )
    )

    private val testObjectStorageDelete = keyValueStorage<UnSerializable>(
        "test-object", entityQueries, mainScope, config = KeyValueStorage.Config(
            deserializePolicy = KeyValueStorage.Config.DeserializePolicy.DELETE
        )
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun deserializeWithError() = runTest {
        val expected = TestObject()
        testObjectStorage.insert(expected.id, expected)
        val actual = testObjectStorage.selectAll().first().first()
        assertEquals(expected, actual)

        try {
            testObjectStorageError.selectByKey(expected.id).first()
            fail("Expected exception")
        } catch (e: Exception) {
            assertIs<SerializationException>(e)
        }
    }


    @Test
    fun deserializeWithDelete() = runTest {
        val expected = TestObject()
        testObjectStorage.insert(expected.id, expected)
        val actual = testObjectStorage.selectAll().first().first()
        assertEquals(expected, actual)

        val result = testObjectStorageDelete.selectByKey(expected.id).first()
        assertNull(result)
        val resultList = testObjectStorageDelete.selectAll().first()
        assertEquals(0, resultList.size)

        until { testObjectStorage.count().first() == 0 }
        // Should have been deleted
        assertEquals(0, testObjectStorage.count().first())
    }

}
