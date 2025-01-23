package com.mercury.sqkon.serialization

import com.mercury.sqkon.TestObject
import com.mercury.sqkon.TestSealed
import com.mercury.sqkon.db.serialization.SqkonJson
import kotlin.test.Test
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

}
