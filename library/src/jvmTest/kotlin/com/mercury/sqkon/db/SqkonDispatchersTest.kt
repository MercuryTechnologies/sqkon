package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqkonDispatchersTest {

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
