package com.mercury.sqkon.db

import com.mercury.sqkon.BaseSealed
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestObjectChild
import com.mercury.sqkon.TestSealed
import com.mercury.sqkon.TestValue
import com.mercury.sqkon.TypeOneData
import com.mercury.sqkon.TypeTwoData
import org.junit.Test
import kotlin.test.assertEquals

class JsonPathBuilderTest {

    @Test
    fun build_with_simple_path() {
        val builder = TestObject::class.with(TestObject::child) {
            then(TestObjectChild::createdAt)
        }
        assertEquals(expected = "\$.child.createdAt", actual = builder.buildPath())
    }

    @Test
    fun build_next_simple_path() {
        val builder = TestObject::child.then(TestObjectChild::createdAt)
        assertEquals(expected = "\$.child.createdAt", actual = builder.buildPath())
    }

    @Test
    fun build_builder_simple_path() {
        val builder = TestObject::child.builder {
            then(TestObjectChild::createdAt)
        }
        assertEquals(expected = "\$.child.createdAt", actual = builder.buildPath())
    }

    @Test
    fun build_with_value_class() {
        val builder = TestObject::class.with(TestObject::testValue) {
            then(TestValue::test)
        }
        assertEquals(expected = "\$.testValue", actual = builder.buildPath())
    }

    @Test
    fun build_with_value_class_next() {
        val builder = TestObject::testValue.then(TestValue::test)
        assertEquals(expected = "\$.testValue", actual = builder.buildPath())
    }

    @Test
    fun build_with_value_class_builder() {
        val builder = TestObject::testValue.builder {
            then(TestValue::test)
        }
        assertEquals(expected = "\$.testValue", actual = builder.buildPath())
    }

    @Test
    fun build_with_serialName_class_builder() {
        val builder = TestObject::serialName.builder(serialName = "different_name")
        // Should use the serial name annotation override
        assertEquals(expected = "\$.different_name", actual = builder.buildPath())
    }

    @Test
    fun build_with_sealed_path() {
        val builder = TestObject::sealed.builder {
            then(TestSealed.Impl::boolean) {}
        }
        assertEquals(expected = "\$.sealed[1].boolean", actual = builder.buildPath())
    }

    @Test
    fun build_with_sealed_inline_value_path() {
        val builder = TestObject::sealed.builder {
            then(TestSealed.Impl2::value) {}
        }
        assertEquals(expected = "\$.sealed[1]", actual = builder.buildPath())
    }

    @Test
    fun build_with_base_sealed_value_path() {
        val builder = BaseSealed::class.with(BaseSealed.TypeOne::data) {
            then(TypeOneData::key)
        }
        assertEquals(expected = "\$[1].key", actual = builder.buildPath())
    }

    @Test
    fun build_with_base_sealed_data_path() {
        val builder = BaseSealed::class.with(BaseSealed.TypeTwo::data) {
            then(TypeTwoData::key)
        }
        assertEquals(expected = "\$[1].data.key", actual = builder.buildPath())
    }

    @Test
    fun build_with_list_builder() {
        val builder = TestObject::list.builderFromList {
            then(TestObjectChild::createdAt)
        }
        assertEquals(expected = "\$.list[%].createdAt", actual = builder.buildPath())
    }

    @Test
    fun build_with_list_then() {
        val builder = TestObject::list.thenFromList(TestObjectChild::createdAt)
        assertEquals(expected = "\$.list[%].createdAt", actual = builder.buildPath())
    }

    @Test
    fun build_with_list_path() {
        val builder = TestObject::class.withList(TestObject::list) {
            then(TestObjectChild::createdAt)
        }
        assertEquals(expected = "\$.list[%].createdAt", actual = builder.buildPath())
    }

}
