package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class SqkonDispatchersTest {

    private val mainScope = MainScope()

    @AfterTest
    fun tearDown() { mainScope.cancel() }

    @Test
    fun defaultDispatchers_areReachable() {
        val d = defaultSqkonDispatchers
        assertNotSame(d.read, d.write)
    }

    @Test
    fun customDispatchers_areThreadedThroughSqkonConstructor() = runTest {
        val testRead = StandardTestDispatcher(testScheduler, name = "test-read")
        val testWrite = StandardTestDispatcher(testScheduler, name = "test-write")
        val dispatchers = SqkonDispatchers(read = testRead, write = testWrite)

        val sqkon = Sqkon(
            scope = TestScope(testScheduler),
            dispatchers = dispatchers,
        )

        val store = sqkon.keyValueStorage<TestObject>("dispatchers-test")
        store.insert("k", TestObject(id = "k", name = "v"))
        val read = store.selectByKey("k").first()
        assertEquals("v", read?.name)
    }
}
