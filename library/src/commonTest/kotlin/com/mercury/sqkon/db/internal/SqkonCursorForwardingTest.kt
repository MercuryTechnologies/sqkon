package com.mercury.sqkon.db.internal

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import com.mercury.sqkon.db.internal.sqldelight.SqlDelightSqkonCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqkonCursorForwardingTest {

    private val rows = listOf<Map<Int, Any?>>(
        mapOf(0 to 1L, 1 to "a", 2 to byteArrayOf(7), 3 to true, 4 to 0.5),
        mapOf(0 to 2L, 1 to null, 2 to null, 3 to false, 4 to null),
    )
    private var idx = -1
    private val spy = object : SqlCursor {
        override fun next(): QueryResult<Boolean> {
            idx++
            return QueryResult.Value(idx < rows.size)
        }
        override fun getString(index: Int): String? = rows[idx][index] as String?
        override fun getLong(index: Int): Long? = rows[idx][index] as Long?
        override fun getBytes(index: Int): ByteArray? = rows[idx][index] as ByteArray?
        override fun getBoolean(index: Int): Boolean? = rows[idx][index] as Boolean?
        override fun getDouble(index: Int): Double? = rows[idx][index] as Double?
    }

    @Test
    fun forwards_next_and_all_getters() {
        val cursor: SqkonCursor = SqlDelightSqkonCursor(spy)
        assertTrue(cursor.next())
        assertEquals(1L, cursor.getLong(0))
        assertEquals("a", cursor.getString(1))
        assertEquals(7.toByte(), cursor.getBytes(2)!![0])
        assertEquals(true, cursor.getBoolean(3))
        assertEquals(0.5, cursor.getDouble(4))

        assertTrue(cursor.next())
        assertEquals(2L, cursor.getLong(0))
        assertEquals(null, cursor.getString(1))
        assertEquals(null, cursor.getBytes(2))
        assertEquals(false, cursor.getBoolean(3))
        assertEquals(null, cursor.getDouble(4))

        assertEquals(false, cursor.next())
    }
}
