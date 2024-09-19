package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import com.mercury.sqkon.TestValue
import org.junit.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals

class JsonPathTest {

    @Test
    fun `build simple path`() {
        val path = TestObject::child then TestObjectChild::createdAt
        assertEquals(expected = "\$.child.createdAt", actual = path.build())
    }

    @Test
    @Ignore("Not possible right now with KMM reflection to ignore the value class")
    fun `build with value class`() {
        val path = TestObject::testValue then TestValue::test
        assertEquals(expected = "\$.testValue", actual = path.build())
    }

//    Not possible right now
//    @Test
//    fun `build with list`() {
//        val path = TestObject::list then TestObjectChild::createdAt
//        assertEquals(expected = "\$.list.createdAt", actual = path.build())
//    }


}
