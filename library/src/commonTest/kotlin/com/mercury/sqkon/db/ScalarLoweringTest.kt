package com.mercury.sqkon.db

import com.mercury.sqkon.TestObject
import org.junit.Test
import kotlin.test.assertEquals

class ScalarLoweringTest {

    @Test
    fun eq_scalarForm_emitsJsonExtract() {
        val w: Where<TestObject> = TestObject::name eq "Coffee"
        val frag = w.toScalarSqlValue()

        assertEquals("(json_extract(entity.value, ?) = ?)", frag.sql)
        assertEquals(2, frag.parameters)
        assertEquals(
            listOf("\$.name", "Coffee"),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }

    @Test
    fun neq_scalarForm_emitsJsonExtract() {
        val w: Where<TestObject> = TestObject::name neq "Hidden"
        val frag = w.toScalarSqlValue()

        assertEquals("(json_extract(entity.value, ?) != ?)", frag.sql)
        assertEquals(2, frag.parameters)
        assertEquals(
            listOf("\$.name", "Hidden"),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }
}
