package com.mercury.sqkon.serialization

import com.mercury.sqkon.TestEnum
import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestSealed
import com.mercury.sqkon.db.serialization.KotlinSqkonSerializer
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KotlinSqkonSerializerKtTest {

    @Test
    fun testSealedSerialization() {
        val json = SqkonJson { }

        val one = TestObject()
        val two = one.copy(sealed = TestSealed.Impl(boolean = true))

        assertNotEquals(
            json.encodeToString<TestObject>(one).also { println(it) },
            json.encodeToString<TestObject>(two).also { println(it) }
        )

    }

    @Test
    fun testEnumSerialNameSerialization() {
        val serializer = KotlinSqkonSerializer()
        val value = TestEnum.LAST
        assertEquals("\"unknown\"", serializer.serialize(typeOf<TestEnum>(), value))
    }
}
