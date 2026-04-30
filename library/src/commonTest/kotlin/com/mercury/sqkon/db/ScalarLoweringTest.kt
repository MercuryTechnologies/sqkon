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

    @Test
    fun and_scalarForm_concatsChildren() {
        val w = (TestObject::name eq "Coffee").and(TestObject::value gt 100L)
        val frag = w.toScalarSqlValue()
        assertEquals(
            "((json_extract(entity.value, ?) = ?) AND (json_extract(entity.value, ?) > ?))",
            frag.sql,
        )
        assertEquals(4, frag.parameters)
        assertEquals(
            listOf("\$.name", "Coffee", "\$.value", 100L),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }

    @Test
    fun or_scalarForm_concatsChildrenWithOr() {
        val w = (TestObject::name eq "A").or(TestObject::name eq "B")
        val frag = w.toScalarSqlValue()
        assertEquals(
            "((json_extract(entity.value, ?) = ?) OR (json_extract(entity.value, ?) = ?))",
            frag.sql,
        )
    }

    @Test
    fun not_scalarForm_wrapsChild() {
        val w = not(TestObject::name eq "Hidden")
        val frag = w.toScalarSqlValue()
        assertEquals(
            "(NOT (json_extract(entity.value, ?) = ?))",
            frag.sql,
        )
    }

    @Test
    fun like_scalarForm() {
        val w: Where<TestObject> = TestObject::name like "Star%"
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) LIKE ?)", frag.sql)
        assertEquals(listOf("\$.name", "Star%"), captureBoundArgs(frag.parameters, frag.bindArgs))
    }

    @Test
    fun inList_scalarForm() {
        val w: Where<TestObject> = TestObject::name inList listOf("A", "B", "C")
        val frag = w.toScalarSqlValue()
        assertEquals("(json_extract(entity.value, ?) IN (?, ?, ?))", frag.sql)
        assertEquals(4, frag.parameters)
        assertEquals(
            listOf("\$.name", "A", "B", "C"),
            captureBoundArgs(frag.parameters, frag.bindArgs),
        )
    }

    @Test
    fun inList_emptyList_scalarForm_isAlwaysFalse() {
        val w: Where<TestObject> = TestObject::name inList emptyList<String>()
        val frag = w.toScalarSqlValue()
        assertEquals("(0)", frag.sql)
        assertEquals(0, frag.parameters)
    }

    @Test
    fun notInList_emptyList_scalarForm_isAlwaysTrue() {
        val w: Where<TestObject> = TestObject::name.notInList(emptyList<String>())
        val frag = w.toScalarSqlValue()
        assertEquals("(1)", frag.sql)
        assertEquals(0, frag.parameters)
    }
}
