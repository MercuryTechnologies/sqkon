package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import com.mercury.sqkon.TestValue
import org.junit.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals

class JsonPathBuilderTest {

    @Test
    fun `build simple path`() {
        val builder = TestObject::class.with(TestObject::child) {
            then(TestObjectChild::createdAt)
        }
        assertEquals(expected = "\$.child.createdAt", actual = builder.buildPath())
    }

    @Test
    fun `build with value class`() {
        val builder = TestObject::class.with(TestObject::testValue) {
            then(TestValue::test)
        }
        assertEquals(expected = "\$.testValue", actual = builder.buildPath())
    }


    @Test
    @Ignore("Need to expand creating tree json paths")
    fun `build with list`() {
        val builder = TestObject::list.thenFromList(TestObjectChild::createdAt)
        assertEquals(expected = "\$.list[*].createdAt", actual = builder.buildPath())
    }

}
