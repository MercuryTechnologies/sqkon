package com.mercury.sqkon.db.internal.androidx

import androidx.sqlite.SQLiteStatement
import com.mercury.sqkon.db.internal.SqkonCursor

/** SqkonCursor over a stepped [SQLiteStatement]. */
internal class AndroidxSqkonCursor(private val statement: SQLiteStatement) : SqkonCursor {
    override fun next(): Boolean = statement.step()
    override fun getString(index: Int): String? =
        if (statement.isNull(index)) null else statement.getText(index)
    override fun getLong(index: Int): Long? =
        if (statement.isNull(index)) null else statement.getLong(index)
    override fun getBytes(index: Int): ByteArray? =
        if (statement.isNull(index)) null else statement.getBlob(index)
    override fun getDouble(index: Int): Double? =
        if (statement.isNull(index)) null else statement.getDouble(index)
    override fun getBoolean(index: Int): Boolean? =
        if (statement.isNull(index)) null else statement.getLong(index) == 1L
}
