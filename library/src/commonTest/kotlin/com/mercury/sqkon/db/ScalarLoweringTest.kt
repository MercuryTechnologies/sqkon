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

    @Test
    fun gt_scalarForm() {
        val w: Where<TestObject> = TestObject::value gt 100L
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) > ?)", frag.sql)
        assertEquals(listOf("\$.value", 100L), captureBoundArgs(frag.parameters, frag.bindArgs))
    }

    @Test
    fun lt_scalarForm() {
        val w: Where<TestObject> = TestObject::value lt 100L
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) < ?)", frag.sql)
    }

    @Test
    fun eqNull_scalarForm_emitsIsNull() {
        val w: Where<TestObject> = TestObject::name eq null
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) IS NULL)", frag.sql)
        assertEquals(1, frag.parameters)
        assertEquals(listOf("\$.name"), captureBoundArgs(frag.parameters, frag.bindArgs))
    }

    @Test
    fun neqNull_scalarForm_emitsIsNotNull() {
        val w: Where<TestObject> = TestObject::name neq null
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) IS NOT NULL)", frag.sql)
    }
}
