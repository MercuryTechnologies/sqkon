package com.mercury.sqkon.db.internal

import app.cash.sqldelight.db.SqlPreparedStatement
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonStatement
import kotlin.test.Test
import kotlin.test.assertEquals

class SqkonStatementForwardingTest {

    private val calls = mutableListOf<Pair<String, Any?>>()
    private val spy = object : SqlPreparedStatement {
        override fun bindBytes(index: Int, bytes: ByteArray?) {
            calls += "bindBytes:$index" to bytes
        }

        override fun bindBoolean(index: Int, boolean: Boolean?) {
            calls += "bindBoolean:$index" to boolean
        }

        override fun bindDouble(index: Int, double: Double?) {
            calls += "bindDouble:$index" to double
        }

        override fun bindLong(index: Int, long: Long?) {
            calls += "bindLong:$index" to long
        }

        override fun bindString(index: Int, string: String?) {
            calls += "bindString:$index" to string
        }
    }

    @Test
    fun forwards_every_bind_one_to_one() {
        val stmt: SqkonStatement = SqlDelightSqkonStatement(spy)
        stmt.bindLong(0, 42L)
        stmt.bindString(1, "x")
        stmt.bindBytes(2, byteArrayOf(1))
        stmt.bindBoolean(3, true)
        stmt.bindDouble(4, 1.5)
        assertEquals(5, calls.size)
        assertEquals("bindLong:0", calls[0].first); assertEquals(42L, calls[0].second)
        assertEquals("bindString:1", calls[1].first); assertEquals("x", calls[1].second)
        assertEquals("bindBytes:2", calls[2].first)
        assertEquals("bindBoolean:3", calls[3].first); assertEquals(true, calls[3].second)
        assertEquals("bindDouble:4", calls[4].first); assertEquals(1.5, calls[4].second)
    }

    @Test
    fun forwards_nulls() {
        val stmt: SqkonStatement = SqlDelightSqkonStatement(spy)
        stmt.bindLong(0, null)
        stmt.bindString(1, null)
        stmt.bindBytes(2, null)
        stmt.bindBoolean(3, null)
        stmt.bindDouble(4, null)
        assertEquals(5, calls.size)
        calls.forEach { assertEquals(null, it.second) }
    }
}
