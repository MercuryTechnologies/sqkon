@file:OptIn(ExperimentalUuidApi::class)

package com.mercury.sqkon.db

import com.mercury.sqkon.TestEnum
import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

class KeyValueStorageEnumTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @AfterTest
    fun tearDown() {
        mainScope.cancel()
    }


    @Test
    fun select_byEnum() = runTest {
        val expected = (1..10).map {
            TestObject(testEnum = TestEnum.FIRST)
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val bySecondEnum = testObjectStorage.select(
            where = TestObject::testEnum eq TestEnum.SECOND,
        ).first()
        val byFirstEnum = testObjectStorage.select(
            where = TestObject::testEnum eq TestEnum.FIRST,
        ).first()

        assertEquals(0, bySecondEnum.size)
        assertEquals(expected.size, byFirstEnum.size)
    }

    @Test
    fun select_bySerialNameEnum() = runTest {
        val expected = (1..10).map {
            TestObject(testEnum = TestEnum.LAST)
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val bySecondEnum = testObjectStorage.select(
            where = TestObject::testEnum eq TestEnum.SECOND,
        ).first()
        // Broken due to lack of serialName support from descriptors
        val byLastEnum = testObjectStorage.select(
            where = TestObject::testEnum eq "unknown",
        ).first()

        assertEquals(0, bySecondEnum.size)
        assertEquals(expected.size, byLastEnum.size)
    }


}
