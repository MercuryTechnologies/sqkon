@file:OptIn(ExperimentalUuidApi::class)

package com.mercury.sqkon.db

import app.cash.sqldelight.logs.LogSqliteDriver
import com.mercury.sqkon.BaseSealed
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestSealed
import com.mercury.sqkon.TypeOneData
import com.mercury.sqkon.TypeTwoData
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class KeyValueStorageSealedTest {

    private val mainScope = MainScope()
    private val driver = LogSqliteDriver(driverFactory().createDriver()) {
        println(it)
    }
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val testObjectStorage = keyValueStorage<TestObject>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    private val baseSealedStorage = keyValueStorage<BaseSealed>(
        "test-object", entityQueries, metadataQueries, mainScope
    )

    @AfterTest
    fun tearDown() {
        mainScope.cancel()
    }


    @Test
    fun select_byEntitySealedImpl() = runTest {
        val expected = (1..10).map {
            TestObject(sealed = TestSealed.Impl(boolean = true))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actualBySealedBooleanFalse = testObjectStorage.select(
            where = TestObject::sealed.then(TestSealed.Impl::boolean) eq false,
        ).first()
        val actualBySealedBooleanTrue = testObjectStorage.select(
            where = TestObject::sealed.then(TestSealed.Impl::boolean) eq true,
        ).first()

        assertEquals(0, actualBySealedBooleanFalse.size)
        assertEquals(expected.size, actualBySealedBooleanTrue.size)
    }

    @Test
    fun select_byEntitySealedImpl2() = runTest {
        val expected = (1..10).map {
            TestObject(sealed = TestSealed.Impl2(value = "test value"))
        }.associateBy { it.id }
        testObjectStorage.insertAll(expected)
        val actualBySealedValue1 = testObjectStorage.select(
            where = TestObject::sealed.then(TestSealed.Impl2::value) eq "test",
        ).first()
        val actualBySealedValue2 = testObjectStorage.select(
            where = TestObject::sealed.then(TestSealed.Impl2::value) eq "test value",
        ).first()

        assertEquals(0, actualBySealedValue1.size)
        assertEquals(expected.size, actualBySealedValue2.size)
    }

    @Test
    fun select_byBaseSealedId() = runTest {
        val expectedT1 = (1..10).map {
            BaseSealed.TypeOne(
                data = TypeOneData(key = it.toString(), value = Uuid.random().toString())
            )
        }.associateBy { it.id }
        val exceptedT2 = (11..20).map {
            BaseSealed.TypeTwo(
                data = TypeTwoData(key = it.toString(), otherValue = it)
            )
        }.associateBy { it.id }
        baseSealedStorage.insertAll(expectedT1 + exceptedT2)
        val count = baseSealedStorage.count().first()
        assertEquals(20, count)

        val actualT1 = baseSealedStorage.select(
            where = BaseSealed::class.with(BaseSealed.TypeOne::data) {
                then(TypeOneData::key)
            } eq "1",
        ).first()
        assertEquals(1, actualT1.size)
        assertEquals(expectedT1["1"], actualT1.first() as BaseSealed.TypeOne)
        val actualT2 = baseSealedStorage.select(
            where = BaseSealed::class.with(BaseSealed.TypeTwo::data) {
                then(TypeTwoData::key)
            } eq "11",
        ).first()
        assertEquals(1, actualT2.size)
        assertEquals(exceptedT2["11"], actualT2.first() as BaseSealed.TypeTwo)

        // Can't search on a getter
        baseSealedStorage.select(where = BaseSealed::id.eq("1")).first().also {
            assertEquals(0, it.size)
        }
    }
}
